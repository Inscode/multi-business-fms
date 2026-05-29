"""
LangChain Tools — each function is a "skill" the LLM can choose to call.

HOW TOOL CALLING WORKS:
  1. LLM reads the docstring to understand what the tool does.
  2. LLM decides: "I need bill data" → calls get_unconfirmed_bills()
  3. Our Python code runs, hits the Spring Boot API, returns a string.
  4. That string goes back to the LLM as context to form the final answer.

The @tool decorator does all the wiring — it turns a plain Python
function into a LangChain tool with a name, description, and schema.
"""

import time
from datetime import datetime, timedelta

import httpx
from langchain_core.tools import tool

from app.config import SPRING_API_URL, SERVICE_USERNAME, SERVICE_PASSWORD


# ─── Auth token cache ─────────────────────────────────────────────────────────
# We log in once as a service account and reuse the JWT for ~1 hour.
# This avoids a login request on every single tool call.

_token_cache: dict = {"token": None, "expires_at": 0.0}


def _get_headers() -> dict:
    """Return Authorization header, refreshing the JWT if expired."""
    if _token_cache["token"] and time.time() < _token_cache["expires_at"]:
        return {"Authorization": f"Bearer {_token_cache['token']}"}

    resp = httpx.post(
        f"{SPRING_API_URL}/auth/login",
        json={"username": SERVICE_USERNAME, "password": SERVICE_PASSWORD},
        timeout=10,
    )
    resp.raise_for_status()
    token = resp.json()["token"]
    _token_cache["token"] = token
    _token_cache["expires_at"] = time.time() + 3500  # refresh just before 1-hour expiry
    return {"Authorization": f"Bearer {token}"}


def _get(path: str) -> list | dict:
    """GET helper — calls Spring Boot API and returns parsed JSON."""
    resp = httpx.get(f"{SPRING_API_URL}{path}", headers=_get_headers(), timeout=15)
    resp.raise_for_status()
    return resp.json()


# ─── Tool 1: Unconfirmed Bills ─────────────────────────────────────────────────

@tool
def get_unconfirmed_bills() -> str:
    """
    Get all bills that have NOT been completed or cancelled.
    Use this when asked about: pending bills, unconfirmed bills,
    outstanding work, or which bills still need action.
    """
    try:
        bills: list = _get("/bills")

        # Filter out final states
        pending = [
            b for b in bills
            if b.get("status") not in ("COMPLETED", "CANCELLED")
        ]

        if not pending:
            return "No unconfirmed bills found. All bills are completed or cancelled."

        # Group by status so the LLM can give a useful breakdown
        by_status: dict[str, list] = {}
        for b in pending:
            by_status.setdefault(b["status"], []).append(b)

        result = f"Found {len(pending)} unconfirmed bills:\n\n"

        for status, group in sorted(by_status.items()):
            result += f"[{status}] — {len(group)} bills\n"
            for b in group[:5]:  # show up to 5 per status
                result += (
                    f"  • {b['billNumber']} | {b['customerName']} | "
                    f"{b.get('area', 'N/A')} | Rs {b['balanceRemaining']:,.0f} remaining\n"
                )
            if len(group) > 5:
                result += f"  ...and {len(group) - 5} more in this status\n"
            result += "\n"

        return result.strip()

    except Exception as e:
        return f"Error fetching bills: {str(e)}"


# ─── Tool 2: Revenue by Area ───────────────────────────────────────────────────

@tool
def get_revenue_by_area() -> str:
    """
    Get total confirmed income grouped by area, ranked highest to lowest.
    Use this when asked: which area generates most income, revenue breakdown,
    area performance, or geographic sales analysis.
    """
    try:
        # We need both bills (for area info) and payments (for amounts)
        bills: list  = _get("/bills")
        payments: list = _get("/payments")

        # Build a lookup: billNumber → area
        bill_area: dict[str, str] = {
            b["billNumber"]: b.get("area", "Unknown")
            for b in bills
        }

        # Only count confirmed payments
        confirmed = [p for p in payments if p.get("status") == "CONFIRMED"]

        if not confirmed:
            return "No confirmed payments found yet."

        area_totals: dict[str, float] = {}
        area_count: dict[str, int] = {}

        for p in confirmed:
            area = bill_area.get(p.get("billNumber"), "Unknown")
            area_totals[area] = area_totals.get(area, 0.0) + p["paymentAmount"]
            area_count[area]  = area_count.get(area, 0) + 1

        grand_total = sum(area_totals.values())
        ranked = sorted(area_totals.items(), key=lambda x: x[1], reverse=True)

        result = f"Revenue by area ({len(confirmed)} confirmed payments, total Rs {grand_total:,.0f}):\n\n"
        for rank, (area, amount) in enumerate(ranked, 1):
            pct = (amount / grand_total) * 100
            count = area_count[area]
            result += (
                f"#{rank} {area}: Rs {amount:,.0f} "
                f"({pct:.1f}% of total | {count} payments)\n"
            )

        return result

    except Exception as e:
        return f"Error fetching revenue data: {str(e)}"


# ─── Tool 3: Overdue Bills ─────────────────────────────────────────────────────

@tool
def get_overdue_bills(days: int = 60) -> str:
    """
    Get bills that have an unpaid balance AND are older than a given number of days.
    Use this when asked about: overdue bills, old unpaid bills, bills past X days,
    or long-outstanding accounts.

    Args:
        days: Age threshold in days. Bills older than this with unpaid balance are returned.
              Default is 60 days.
    """
    try:
        bills: list = _get("/bills")
        cutoff = datetime.now() - timedelta(days=days)

        overdue = []
        for b in bills:
            # Skip if fully paid or in a terminal state
            if b.get("balanceRemaining", 0) <= 0:
                continue
            if b.get("status") in ("CANCELLED", "COMPLETED"):
                continue

            raw_date = b.get("billDate", "")
            if not raw_date:
                continue

            try:
                # Handle both "2024-01-15" and "2024-01-15T00:00:00" formats
                bill_date = datetime.fromisoformat(raw_date.replace("Z", "").split("T")[0])
            except ValueError:
                continue

            if bill_date < cutoff:
                days_old = (datetime.now() - bill_date).days
                overdue.append({**b, "_days_old": days_old})

        if not overdue:
            return f"No unpaid bills older than {days} days. 👍"

        # Sort: oldest first
        overdue.sort(key=lambda x: x["_days_old"], reverse=True)

        total_outstanding = sum(b["balanceRemaining"] for b in overdue)
        result = (
            f"Found {len(overdue)} overdue bills (>{days} days unpaid) "
            f"totalling Rs {total_outstanding:,.0f}:\n\n"
        )

        for b in overdue[:20]:
            result += (
                f"• {b['billNumber']} | {b['customerName']} | "
                f"{b.get('area', 'N/A')} | Rs {b['balanceRemaining']:,.0f} due | "
                f"{b['_days_old']} days old | {b['status']}\n"
            )
        if len(overdue) > 20:
            result += f"\n...and {len(overdue) - 20} more."

        return result

    except Exception as e:
        return f"Error fetching overdue bills: {str(e)}"


# ─── Tool 4: Payment Summary ───────────────────────────────────────────────────

@tool
def get_payment_summary() -> str:
    """
    Get a full financial overview: payment totals by status, outstanding balances,
    and business-level breakdown.
    Use this when asked about: payment statistics, financial overview, total income,
    how much is confirmed/pending, or overall business health.
    """
    try:
        payments: list = _get("/payments")
        bills: list    = _get("/bills")

        # ── Payment breakdown by status ──
        by_status: dict[str, dict] = {}
        for p in payments:
            s = p.get("status", "UNKNOWN")
            if s not in by_status:
                by_status[s] = {"count": 0, "total": 0.0}
            by_status[s]["count"] += 1
            by_status[s]["total"] += p.get("paymentAmount", 0)

        result = "── Payment Summary ──\n\n"
        for status in ("CONFIRMED", "ENTERED", "REJECTED"):
            data = by_status.get(status, {"count": 0, "total": 0.0})
            result += f"{status}: {data['count']} payments | Rs {data['total']:,.0f}\n"

        grand = sum(v["total"] for v in by_status.values())
        result += f"\nAll payments combined: Rs {grand:,.0f}\n"

        # ── Bills outstanding ──
        active_bills = [b for b in bills if b.get("status") not in ("CANCELLED",)]
        total_value  = sum(b.get("totalAmount", 0) for b in active_bills)
        outstanding  = sum(b.get("balanceRemaining", 0) for b in active_bills if b.get("balanceRemaining", 0) > 0)
        fully_paid   = sum(1 for b in active_bills if b.get("balanceRemaining", 0) == 0)

        result += f"\n── Bills Overview ──\n\n"
        result += f"Total bill value:   Rs {total_value:,.0f}\n"
        result += f"Outstanding:        Rs {outstanding:,.0f}\n"
        result += f"Fully paid bills:   {fully_paid} of {len(active_bills)}\n"

        # ── Per-business breakdown ──
        biz_totals: dict[str, float] = {}
        for b in active_bills:
            biz = b.get("business", "Unknown")
            biz_totals[biz] = biz_totals.get(biz, 0.0) + b.get("totalAmount", 0)

        result += "\n── By Business ──\n\n"
        for biz, total in sorted(biz_totals.items(), key=lambda x: x[1], reverse=True):
            result += f"{biz}: Rs {total:,.0f}\n"

        return result

    except Exception as e:
        return f"Error fetching payment summary: {str(e)}"


# ─── Tool 5: Worker Performance ───────────────────────────────────────────────

@tool
def get_worker_performance() -> str:
    """
    Get a summary of how many bills are assigned to each worker, their completion
    rate, and outstanding amounts.
    Use this when asked about: workers, staff performance, who has the most bills,
    worker assignments, or delivery team status.
    """
    try:
        bills: list = _get("/bills")

        workers: dict[str, dict] = {}

        for b in bills:
            name = b.get("workerName") or "Unassigned"
            if name not in workers:
                workers[name] = {"total": 0, "completed": 0, "outstanding": 0.0}
            workers[name]["total"] += 1
            if b.get("status") == "COMPLETED":
                workers[name]["completed"] += 1
            workers[name]["outstanding"] += b.get("balanceRemaining", 0)

        if not workers:
            return "No worker data found."

        result = f"Worker Performance ({len(workers)} workers):\n\n"
        for name, d in sorted(workers.items(), key=lambda x: x[1]["total"], reverse=True):
            completion_rate = (d["completed"] / d["total"] * 100) if d["total"] else 0
            result += (
                f"• {name}\n"
                f"  Bills: {d['total']} total | {d['completed']} completed "
                f"({completion_rate:.0f}%) | Rs {d['outstanding']:,.0f} outstanding\n"
            )

        return result

    except Exception as e:
        return f"Error fetching worker data: {str(e)}"


# ─── All tools in one list (imported by agent.py) ─────────────────────────────

ALL_TOOLS = [
    get_unconfirmed_bills,
    get_revenue_by_area,
    get_overdue_bills,
    get_payment_summary,
    get_worker_performance,
]