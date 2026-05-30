# Stock Management Tab - UI Design

## Overview
The Stock Tab is a new feature for managing shadow stock for RAINCO products only. It's accessible to ADMIN, ACCOUNTANT, and MAIN_ACCOUNTANT (edit), with OWNER having view-only access.

**Route**: `/stock`

## Features

### 1. Summary Load Bill Component
**Location**: `summary-load-bill/summary-load-bill.ts`

**Purpose**: Group multiple SYSTEM bills into a single load summary for lorry shipping.

**Workflow**:
- Step 1: Select unassigned SYSTEM bills from a table (checkbox multi-select)
  - Columns: Bill Number, Amount, Assigned Person, Payment Type, Bill Date
  - Master checkbox to select all
  
- Step 2: View summary of selected bills
  - Number of bills selected
  - Total quantity (if available)
  - Total amount
  
- Step 3: Enter load details
  - Load Date (datepicker)
  - Load Notes (optional)
  
- Step 4: Save
  - Creates a Summary Load Bill
  - Links the selected SYSTEM bills to it
  - Status: PENDING (awaiting admin approval)

**Features**:
- Multi-select with master checkbox
- Summary box showing aggregated data
- Form validation
- Success message on save
- Loading spinner during save

---

### 2. Stock Item Entry Component
**Location**: `stock-item-entry/stock-item-entry.ts`

**Purpose**: Enter individual bills (DRAFT, MANUAL, SYSTEM) with line-by-line items for shadow stock tracking.

**Workflow**:
- Step 1: Select bill source (SYSTEM, DRAFT, MANUAL)
  
- Step 2: Enter bill details
  - Bill Date (datepicker, defaults to today)
  - Notes (optional)
  
- Step 3: Add line items
  - Product name (autocomplete search, ~580 products from return_products)
  - Quantity input
  - Auto-calculated unit price from product master
  - Add button
  - Table showing all added items with actions (delete)
  
- Step 4: Amount verification (for DRAFT/MANUAL only)
  - Show calculated amount (sum of product price × qty)
  - Allow entering actual amount (after discount)
  - Hint: "Can be less than calculated if discount applied"
  
- Step 5: Save
  - Creates the bill with items
  - Status: PENDING (awaiting admin approval)

**Product Search**:
- Autocomplete with product name search
- Shows unit price in the option
- Filters for RAINCO business only
- Handles ~580 products efficiently

**Amount Handling**:
- **SYSTEM bills**: No amount field (just items)
- **DRAFT/MANUAL bills**: 
  - Display calculated amount (read-only)
  - Allow actual amount entry
  - Validate that actual ≤ calculated

**Features**:
- Product autocomplete with real-time search
- Line items table with delete action
- Calculated vs actual amount validation
- Bill source-specific UI adjustments
- Loading spinner during save

---

### 3. End-of-Month Linking Component
**Location**: `end-of-month-linking/end-of-month-linking.ts`

**Purpose**: Match DRAFT/MANUAL bills to SYSTEM bills for period reconciliation.

**Workflow**:
- Step 1: Select unlinked DRAFT/MANUAL bills
  - Table with checkbox multi-select
  - Columns: Bill Number, Source (DRAFT/MANUAL badge), Customer, Amount, Bill Date
  - Master checkbox
  
- Step 2: View selected bills summary
  - Number of bills selected
  - Total amount
  - Expandable list of selected bills with amounts
  
- Step 3: Select or create a SYSTEM bill to link
  - Dropdown with available SYSTEM bills
  - Option to "Create New System Bill"
  - If creating new: enter bill number
  
- Step 4: Verify amount match
  - Shows Draft/Manual Total Amount
  - Shows System Bill Amount
  - Validation:
    - ✓ Green if amounts match
    - ✗ Red with error message if amounts don't match
  - Save button disabled until amounts match
  
- Step 5: Save linking
  - Links selected DRAFT/MANUAL to the SYSTEM bill
  - Status: PENDING (awaiting admin approval)

**Amount Matching Rule**:
- Sum of selected DRAFT/MANUAL amounts must equal the SYSTEM bill amount
- If amounts don't match, save is disabled
- Clear error message shown

**Features**:
- Multi-select with master checkbox
- Amount validation with visual feedback
- Expandable bills list
- Badge styling for bill sources
- Clear error handling
- Success confirmation

---

## UI Components Used

All components use **Angular Material** for consistency:
- `MatTabsModule` - Main tab container
- `MatCardModule` - Card containers
- `MatTableModule` - Bill tables with sorting
- `MatCheckboxModule` - Multi-select
- `MatButtonModule` - Actions
- `MatFormFieldModule` - Form fields
- `MatInputModule` - Text inputs
- `MatSelectModule` - Dropdowns
- `MatAutocompleteModule` - Product search
- `MatDatepickerModule` - Date pickers
- `MatIconModule` - Icons
- `MatProgressSpinnerModule` - Loading spinners
- `MatExpansionModule` - Collapsible sections
- `MatTooltipModule` - Hover tooltips

---

## Key Design Decisions

### 1. Product Search (not dropdown)
- ~580 products too many for dropdown
- Autocomplete search provides better UX
- Real-time filtering as user types

### 2. Amount Handling Differs by Bill Source
- **SYSTEM**: No amount field (just items)
- **DRAFT/MANUAL**: Both calculated and actual amounts
  - Accountant sees what it should be
  - Can adjust for discounts
  - Admin verifies before approval

### 3. Multi-Step Workflows
- Each workflow broken into clear steps
- Forms validated at each step
- Save button disabled until all validations pass
- Success message after save

### 4. Summary Visualizations
- Amount boxes with highlighting
- Validation messages with icons
- Color coding (green for success, red for errors)
- Badge styling for bill sources

### 5. Admin Approval Pattern
- All entries go to PENDING status
- Admin must approve before taking effect
- Approval happens in a separate flow (not shown here)

---

## Future Backend Integration

Currently using mock data. Will need to replace with:

**Services to implement** (in `stock.ts`):
- `getRaincoProducts()` - Fetch from return_products
- `getUnassignedSystemBills()` - Fetch unassigned SYSTEM bills
- `createSummaryLoadBill()` - Create summary load
- `createStockBill()` - Create stock bill with items
- `getUnlinkedBills()` - Fetch unlinked DRAFT/MANUAL bills
- `linkBills()` - Link DRAFT/MANUAL to SYSTEM

**Backend Entities Needed**:
- `ShadowStockMovement` - Track all deductions/additions
- `BillStockLink` - Link DRAFT/MANUAL to SYSTEM
- `SummaryLoadBill` - Group SYSTEM bills
- `BillStockItem` - Line items per bill
- `StockApproval` - Admin approval workflow

---

## Styling

All components follow Material Design with:
- Consistent color palette (primary blue #1976d2)
- Clear section dividers with background colors
- Proper spacing and padding
- Responsive layouts
- Accessibility considerations (WCAG)

---

## Testing Checklist

- [ ] Summary Load Bill - Select/deselect bills
- [ ] Summary Load Bill - Calculate totals correctly
- [ ] Summary Load Bill - Save and create summary
- [ ] Stock Item Entry - Product search autocomplete
- [ ] Stock Item Entry - Add/remove line items
- [ ] Stock Item Entry - Calculate amounts for DRAFT/MANUAL
- [ ] Stock Item Entry - Validate actual amount ≤ calculated
- [ ] Stock Item Entry - Save bill with items
- [ ] End-of-Month - Select/deselect bills
- [ ] End-of-Month - Amount matching validation
- [ ] End-of-Month - Create new system bill option
- [ ] End-of-Month - Save linking
- [ ] All components - Loading states
- [ ] All components - Success messages
- [ ] All components - Form validation

---

## Notes

- Forms currently use mock data
- All console.log statements show what would be sent to backend
- Success delays are simulated (remove in production)
- All components have ChangeDetectionStrategy.OnPush for performance
- Standalone components (no module dependencies)
