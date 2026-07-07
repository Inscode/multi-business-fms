// Static mock data served to demo users — no backend calls made.
// All values are fictional and safe to show publicly.

const today = new Date().toISOString().split('T')[0];
const daysAgo = (n: number) => {
  const d = new Date(); d.setDate(d.getDate() - n); return d.toISOString().split('T')[0];
};

export const DEMO_BILLS = [
  { id: 101, billNumber: 'DEMO-001', business: 'DEMO', division: 'STORE', billType: 'CREDIT', billSource: 'MANUAL', customerName: 'Amal Perera', totalAmount: 45800, amountPaid: 12000, balanceRemaining: 33800, fullyPaid: false, status: 'CREATED', workerId: 1, workerName: 'Saman Kumara', enteredByName: 'Demo Admin', receivedByName: null, receivedAt: null, area: 'Kandy', billDate: daysAgo(32), notes: null, createdAt: daysAgo(32), collectionOnly: false, willBeLinked: false, stockCleared: false },
  { id: 102, billNumber: 'DEMO-002', business: 'DEMO', division: 'STORE', billType: 'CREDIT', billSource: 'MANUAL', customerName: 'Sunil Fernando', totalAmount: 78500, amountPaid: 78500, balanceRemaining: 0, fullyPaid: true, status: 'COMPLETED', workerId: 2, workerName: 'Nimal Perera', enteredByName: 'Demo Admin', receivedByName: 'Demo Accountant', receivedAt: daysAgo(5), area: 'Colombo 3', billDate: daysAgo(45), notes: null, createdAt: daysAgo(45), collectionOnly: false, willBeLinked: false, stockCleared: false },
  { id: 103, billNumber: 'DEMO-003', business: 'DEMO', division: 'STORE', billType: 'CREDIT', billSource: 'MANUAL', customerName: 'Nimali De Silva', totalAmount: 32400, amountPaid: 0, balanceRemaining: 32400, fullyPaid: false, status: 'CREATED', workerId: 1, workerName: 'Saman Kumara', enteredByName: 'Demo Admin', receivedByName: null, receivedAt: null, area: 'Galle', billDate: daysAgo(12), notes: null, createdAt: daysAgo(12), collectionOnly: false, willBeLinked: false, stockCleared: false },
  { id: 104, billNumber: 'DEMO-004', business: 'DEMO', division: 'STORE', billType: 'CREDIT', billSource: 'MANUAL', customerName: 'Chamara Wijeratne', totalAmount: 91200, amountPaid: 45000, balanceRemaining: 46200, fullyPaid: false, status: 'CREATED', workerId: 2, workerName: 'Nimal Perera', enteredByName: 'Demo Admin', receivedByName: null, receivedAt: null, area: 'Kurunegala', billDate: daysAgo(28), notes: null, createdAt: daysAgo(28), collectionOnly: false, willBeLinked: false, stockCleared: false },
  { id: 105, billNumber: 'DEMO-005', business: 'DEMO', division: 'STORE', billType: 'CREDIT', billSource: 'MANUAL', customerName: 'Priya Ratnayake', totalAmount: 56750, amountPaid: 56750, balanceRemaining: 0, fullyPaid: true, status: 'COMPLETED', workerId: null, workerName: null, enteredByName: 'Demo Admin', receivedByName: 'Demo Accountant', receivedAt: daysAgo(3), area: 'Matara', billDate: daysAgo(50), notes: null, createdAt: daysAgo(50), collectionOnly: false, willBeLinked: false, stockCleared: false },
  { id: 106, billNumber: 'DEMO-006', business: 'DEMO', division: 'STORE', billType: 'CREDIT', billSource: 'MANUAL', customerName: 'Roshan Silva', totalAmount: 23100, amountPaid: 10000, balanceRemaining: 13100, fullyPaid: false, status: 'CREATED', workerId: 1, workerName: 'Saman Kumara', enteredByName: 'Demo Admin', receivedByName: null, receivedAt: null, area: 'Negombo', billDate: daysAgo(18), notes: null, createdAt: daysAgo(18), collectionOnly: false, willBeLinked: false, stockCleared: false },
  { id: 107, billNumber: 'DEMO-007', business: 'DEMO', division: 'STORE', billType: 'CREDIT', billSource: 'MANUAL', customerName: 'Dilani Jayawardena', totalAmount: 67300, amountPaid: 0, balanceRemaining: 67300, fullyPaid: false, status: 'ASSIGNED', workerId: 2, workerName: 'Nimal Perera', enteredByName: 'Demo Admin', receivedByName: null, receivedAt: null, area: 'Badulla', billDate: daysAgo(8), notes: null, createdAt: daysAgo(8), collectionOnly: false, willBeLinked: false, stockCleared: false },
  { id: 108, billNumber: 'DEMO-008', business: 'DEMO', division: 'STORE', billType: 'CREDIT', billSource: 'MANUAL', customerName: 'Lakshan Mendis', totalAmount: 41900, amountPaid: 41900, balanceRemaining: 0, fullyPaid: true, status: 'COMPLETED', workerId: null, workerName: null, enteredByName: 'Demo Admin', receivedByName: 'Demo Accountant', receivedAt: daysAgo(7), area: 'Ratnapura', billDate: daysAgo(55), notes: null, createdAt: daysAgo(55), collectionOnly: false, willBeLinked: false, stockCleared: false },
  { id: 109, billNumber: 'DEMO-009', business: 'DEMO', division: 'STORE', billType: 'CREDIT', billSource: 'MANUAL', customerName: 'Samanthi Kumari', totalAmount: 88600, amountPaid: 25000, balanceRemaining: 63600, fullyPaid: false, status: 'CREATED', workerId: 1, workerName: 'Saman Kumara', enteredByName: 'Demo Admin', receivedByName: null, receivedAt: null, area: 'Jaffna', billDate: daysAgo(40), notes: null, createdAt: daysAgo(40), collectionOnly: false, willBeLinked: false, stockCleared: false },
  { id: 110, billNumber: 'DEMO-010', business: 'DEMO', division: 'STORE', billType: 'CREDIT', billSource: 'MANUAL', customerName: 'Ruwan Bandara', totalAmount: 35200, amountPaid: 35200, balanceRemaining: 0, fullyPaid: true, status: 'COMPLETED', workerId: null, workerName: null, enteredByName: 'Demo Admin', receivedByName: 'Demo Accountant', receivedAt: daysAgo(2), area: 'Anuradhapura', billDate: daysAgo(38), notes: null, createdAt: daysAgo(38), collectionOnly: false, willBeLinked: false, stockCleared: false },
  { id: 111, billNumber: 'DEMO-011', business: 'DEMO', division: 'STORE', billType: 'CREDIT', billSource: 'MANUAL', customerName: 'Kasun Pathirana', totalAmount: 52400, amountPaid: 15000, balanceRemaining: 37400, fullyPaid: false, status: 'ASSIGNED', workerId: 2, workerName: 'Nimal Perera', enteredByName: 'Demo Admin', receivedByName: null, receivedAt: null, area: 'Nuwara Eliya', billDate: daysAgo(22), notes: null, createdAt: daysAgo(22), collectionOnly: false, willBeLinked: false, stockCleared: false },
  { id: 112, billNumber: 'DEMO-012', business: 'DEMO', division: 'STORE', billType: 'CREDIT', billSource: 'MANUAL', customerName: 'Thilini Madushani', totalAmount: 29800, amountPaid: 0, balanceRemaining: 29800, fullyPaid: false, status: 'CREATED', workerId: null, workerName: null, enteredByName: 'Demo Admin', receivedByName: null, receivedAt: null, area: 'Polonnaruwa', billDate: daysAgo(5), notes: null, createdAt: daysAgo(5), collectionOnly: false, willBeLinked: false, stockCleared: false },
];

export const DEMO_WORKERS = [
  { id: 1, fullName: 'Saman Kumara', workerType: 'SALES_REP', business: 'DEMO', division: 'STORE', active: true, billAssignable: true, phoneNumber: '077-1234567', area: 'Kandy / Kurunegala', notes: null },
  { id: 2, fullName: 'Nimal Perera', workerType: 'SALES_REP', business: 'DEMO', division: 'STORE', active: true, billAssignable: true, phoneNumber: '071-9876543', area: 'Galle / Badulla', notes: null },
  { id: 3, fullName: 'Kasun Jayasekara', workerType: 'DELIVERY', business: 'DEMO', division: 'STORE', active: true, billAssignable: false, phoneNumber: '076-5551234', area: 'All Areas', notes: null },
  { id: 4, fullName: 'Nuwan Bandara', workerType: 'COLLECTION', business: 'DEMO', division: 'STORE', active: true, billAssignable: false, phoneNumber: '070-3334455', area: 'Colombo', notes: null },
];

export const DEMO_PAYMENTS = [
  { id: 201, billId: 101, billNumber: 'DEMO-001', customerName: 'Amal Perera', amount: 12000, paymentType: 'CASH', status: 'CONFIRMED', isPartial: true, enteredByName: 'Demo Accountant', paymentDate: daysAgo(25), chequeNumber: null, bankName: null, createdAt: daysAgo(25) },
  { id: 202, billId: 102, billNumber: 'DEMO-002', customerName: 'Sunil Fernando', amount: 78500, paymentType: 'CHEQUE', status: 'CONFIRMED', isPartial: false, enteredByName: 'Demo Accountant', paymentDate: daysAgo(5), chequeNumber: 'CHQ-4521', bankName: 'Commercial Bank', createdAt: daysAgo(5) },
  { id: 203, billId: 104, billNumber: 'DEMO-004', customerName: 'Chamara Wijeratne', amount: 45000, paymentType: 'CASH', status: 'CONFIRMED', isPartial: true, enteredByName: 'Demo Accountant', paymentDate: daysAgo(15), chequeNumber: null, bankName: null, createdAt: daysAgo(15) },
  { id: 204, billId: 105, billNumber: 'DEMO-005', customerName: 'Priya Ratnayake', amount: 56750, paymentType: 'CHEQUE', status: 'CONFIRMED', isPartial: false, enteredByName: 'Demo Accountant', paymentDate: daysAgo(3), chequeNumber: 'CHQ-8832', bankName: 'Sampath Bank', createdAt: daysAgo(3) },
  { id: 205, billId: 106, billNumber: 'DEMO-006', customerName: 'Roshan Silva', amount: 10000, paymentType: 'CASH', status: 'ENTERED', isPartial: true, enteredByName: 'Demo Accountant', paymentDate: daysAgo(2), chequeNumber: null, bankName: null, createdAt: daysAgo(2) },
  { id: 206, billId: 108, billNumber: 'DEMO-008', customerName: 'Lakshan Mendis', amount: 41900, paymentType: 'CHEQUE', status: 'CONFIRMED', isPartial: false, enteredByName: 'Demo Accountant', paymentDate: daysAgo(7), chequeNumber: 'CHQ-2210', bankName: 'BOC', createdAt: daysAgo(7) },
  { id: 207, billId: 109, billNumber: 'DEMO-009', customerName: 'Samanthi Kumari', amount: 25000, paymentType: 'BANK_TRANSFER', status: 'ENTERED', isPartial: true, enteredByName: 'Demo Accountant', paymentDate: daysAgo(1), chequeNumber: null, bankName: 'HNB', createdAt: daysAgo(1) },
  { id: 208, billId: 110, billNumber: 'DEMO-010', customerName: 'Ruwan Bandara', amount: 35200, paymentType: 'CASH', status: 'CONFIRMED', isPartial: false, enteredByName: 'Demo Accountant', paymentDate: daysAgo(2), chequeNumber: null, bankName: null, createdAt: daysAgo(2) },
  { id: 209, billId: 111, billNumber: 'DEMO-011', customerName: 'Kasun Pathirana', amount: 15000, paymentType: 'CHEQUE', status: 'ENTERED', isPartial: true, enteredByName: 'Demo Accountant', paymentDate: today, chequeNumber: 'CHQ-9912', bankName: 'People\'s Bank', createdAt: today },
];

export const DEMO_PENDING_PAYMENTS = DEMO_PAYMENTS.filter(p => p.status === 'ENTERED');

export const DEMO_COLLECTION_NOTES = [
  { id: 301, billId: 101, billNumber: 'DEMO-001', customerName: 'Amal Perera', area: 'Kandy', billBalance: 33800, amount: 15000, paymentType: 'CASH', status: 'PENDING', collectedByName: 'Demo Owner', collectedAt: daysAgo(1) + 'T10:00:00', notes: 'Partial collected on visit', chequeNumber: null, bankName: null, branchName: null, chequeDate: null, referenceNumber: null, sourceEntryId: null },
  { id: 302, billId: 109, billNumber: 'DEMO-009', customerName: 'Samanthi Kumari', area: 'Jaffna', billBalance: 63600, amount: 20000, paymentType: 'CHEQUE', status: 'PENDING', collectedByName: 'Demo Owner', collectedAt: daysAgo(1) + 'T14:30:00', notes: null, chequeNumber: 'CHQ-7743', bankName: 'NSB', branchName: 'Jaffna', chequeDate: daysAgo(-7), referenceNumber: null, sourceEntryId: null },
];

export const DEMO_WORKER_COLLECTIONS = [
  { id: 401, bill: { id: 103, billNumber: 'DEMO-003', customerName: 'Nimali De Silva', area: 'Galle', balanceRemaining: 32400 }, amount: 10000, paymentType: 'CASH', status: 'PENDING', workerName: 'Saman Kumara', collectedAt: daysAgo(1) + 'T09:00:00', notes: null },
  { id: 402, bill: { id: 112, billNumber: 'DEMO-012', customerName: 'Thilini Madushani', area: 'Polonnaruwa', balanceRemaining: 29800 }, amount: 5000, paymentType: 'CASH', status: 'PENDING', workerName: 'Nimal Perera', collectedAt: daysAgo(1) + 'T11:30:00', notes: 'Will bring rest next week' },
];

export const DEMO_OWNER_DASHBOARD = {
  totalOutstanding: 295800,
  totalBills: 12,
  activeBills: 8,
  completedBills: 4,
  collectedThisMonth: 212350,
  pendingPayments: DEMO_PENDING_PAYMENTS,
  unassignedBills: DEMO_BILLS.filter(b => !b.workerId && b.status !== 'COMPLETED'),
  pendingCollectionNotes: DEMO_COLLECTION_NOTES,
};

export const DEMO_AGING_REPORT = {
  grandTotalOutstanding: 295800,
  grandOverdue: 155200,
  grandCashPending: 58800,
  grandCashSerious: 33800,
  totalCustomers: 8,
  totalBills: 8,
  topCustomers: [
    { customerName: 'Samanthi Kumari', customerId: null, area: 'Jaffna', totalOutstanding: 63600, overdue: 63600, current: 0, days31to60: 0, days61to90: 63600, days91plus: 0, billCount: 1, oldestBillDate: daysAgo(40), lastPaymentDate: daysAgo(1), cashPending: 0, cashFollowUp: 0, cashUrgent: 63600, cashSerious: 0 },
    { customerName: 'Chamara Wijeratne', customerId: null, area: 'Kurunegala', totalOutstanding: 46200, overdue: 46200, current: 0, days31to60: 46200, days61to90: 0, days91plus: 0, billCount: 1, oldestBillDate: daysAgo(28), lastPaymentDate: daysAgo(15), cashPending: 46200, cashFollowUp: 0, cashUrgent: 0, cashSerious: 0 },
    { customerName: 'Dilani Jayawardena', customerId: null, area: 'Badulla', totalOutstanding: 67300, overdue: 0, current: 67300, days31to60: 0, days61to90: 0, days91plus: 0, billCount: 1, oldestBillDate: daysAgo(8), lastPaymentDate: null, cashPending: 67300, cashFollowUp: 0, cashUrgent: 0, cashSerious: 0 },
  ],
  allCustomers: [],
  byArea: [
    { area: 'Jaffna', totalOutstanding: 63600, overdue: 63600, current: 0, days31to60: 0, days61to90: 63600, days91plus: 0, cashPending: 0, cashSerious: 0, customerCount: 1, billCount: 1, customers: [] },
    { area: 'Badulla', totalOutstanding: 67300, overdue: 0, current: 67300, days31to60: 0, days61to90: 0, days91plus: 0, cashPending: 67300, cashSerious: 0, customerCount: 1, billCount: 1, customers: [] },
    { area: 'Kandy', totalOutstanding: 33800, overdue: 33800, current: 0, days31to60: 33800, days61to90: 0, days91plus: 0, cashPending: 0, cashSerious: 33800, customerCount: 1, billCount: 1, customers: [] },
    { area: 'Kurunegala', totalOutstanding: 46200, overdue: 46200, current: 0, days31to60: 46200, days61to90: 0, days91plus: 0, cashPending: 46200, cashSerious: 0, customerCount: 1, billCount: 1, customers: [] },
    { area: 'Nuwara Eliya', totalOutstanding: 37400, overdue: 37400, current: 0, days31to60: 0, days61to90: 37400, days91plus: 0, cashPending: 0, cashSerious: 37400, customerCount: 1, billCount: 1, customers: [] },
    { area: 'Negombo', totalOutstanding: 13100, overdue: 13100, current: 0, days31to60: 13100, days61to90: 0, days91plus: 0, cashPending: 13100, cashSerious: 0, customerCount: 1, billCount: 1, customers: [] },
    { area: 'Polonnaruwa', totalOutstanding: 29800, overdue: 0, current: 29800, days31to60: 0, days61to90: 0, days91plus: 0, cashPending: 29800, cashSerious: 0, customerCount: 1, billCount: 1, customers: [] },
    { area: 'Galle', totalOutstanding: 32400, overdue: 32400, current: 0, days31to60: 32400, days61to90: 0, days91plus: 0, cashPending: 32400, cashSerious: 0, customerCount: 1, billCount: 1, customers: [] },
  ],
};

export const DEMO_COLLECTION_HEALTH = {
  dsoTrend: [
    { month: '2026-01', avgCollectionDays: 38.2, settledBillCount: 5 },
    { month: '2026-02', avgCollectionDays: 29.7, settledBillCount: 7 },
    { month: '2026-03', avgCollectionDays: 42.1, settledBillCount: 4 },
    { month: '2026-04', avgCollectionDays: 35.0, settledBillCount: 8 },
    { month: '2026-05', avgCollectionDays: 27.5, settledBillCount: 9 },
    { month: '2026-06', avgCollectionDays: 31.3, settledBillCount: 6 },
  ],
  collectorLeaderboard: [
    { workerId: 1, workerName: 'Saman Kumara', totalCollected: 142000, paymentCount: 8, avgDaysToCollect: 22, partialRate: 0.38 },
    { workerId: 2, workerName: 'Nimal Perera', totalCollected: 98500, paymentCount: 6, avgDaysToCollect: 31, partialRate: 0.5 },
  ],
  upcomingCheques: [
    { chequeDate: daysAgo(-3), totalAmount: 56750, count: 1 },
    { chequeDate: daysAgo(-7), totalAmount: 20000, count: 1 },
    { chequeDate: daysAgo(-14), totalAmount: 41900, count: 1 },
  ],
  staleCheques: [],
  riskyCustomers: [
    { customerName: 'Chamara Wijeratne', area: 'Kurunegala', partialCount: 2, returnedCount: 0, currentOutstanding: 46200, riskScore: 2 },
    { customerName: 'Samanthi Kumari', area: 'Jaffna', partialCount: 1, returnedCount: 0, currentOutstanding: 63600, riskScore: 1 },
  ],
};
