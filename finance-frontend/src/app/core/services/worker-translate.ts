import { Injectable, signal } from '@angular/core';

export type Lang = 'en' | 'si' | 'ta';

const TRANSLATIONS: Record<Lang, Record<string, string>> = {
  en: {
    bills: 'Bills', payments: 'Payments', search: 'Search bill or customer...',
    delivered: 'Delivered', not_delivered: 'Not Delivered',
    shop_closed: 'Shop Closed', come_later: 'Come Later',
    add_payment: 'Add Payment', payment_later: 'Payment Later',
    cash: 'Cash', cheque: 'Cheque',
    amount: 'Amount', balance: 'Balance', total_amount: 'Total Amount',
    your_entries: 'My Collections Today', note: 'Note', note_hint: 'e.g. Come next Friday', pay_note_hint: 'Optional note',
    submit: 'Submit', confirm: 'Confirm', edit: 'Edit', delete: 'Delete', cancel: 'Cancel',
    save_note: 'Save Note', cheque_no: 'Cheque No.', bank: 'Bank', branch: 'Branch',
    status_not_visited: 'Not Visited', status_shop_closed: 'Shop Closed',
    status_revisit: 'Come Later', status_delivered_pending: 'Delivered — Awaiting Payment',
    status_delivered_paid: 'Delivered & Paid', status_payment_submitted: 'Payment Submitted',
    pending: 'Pending', confirmed: 'Confirmed', rejected: 'Rejected',
    why_not_delivered: 'Why not delivered?', reason_required: 'Reason is required',
    fully_collected: 'Fully Collected', temp_balance: 'Remaining (your entries)',
    your_collected: 'You collected', management_note: 'Note from office',
    combined_payment: 'Combined Payment', select_bills: 'Select bills',
    filter_all: 'All', filter_not_visited: 'Not Visited', filter_not_delivered: 'Not Delivered',
    filter_awaiting: 'Awaiting', filter_submitted: 'Submitted',
    no_bills: 'No assigned bills', no_payments: 'No payments today',
    loading: 'Loading...', logout: 'Logout',
  },
  si: {
    bills: 'බිල්පත්', payments: 'ගෙවීම්', search: 'බිල් හෝ ගනුදෙනුකරු සොයන්න...',
    delivered: 'බෙදා දෙන ලදී', not_delivered: 'බෙදා නොදුනී',
    shop_closed: 'කඩය වසා ඇත', come_later: 'පසුව එන්න',
    add_payment: 'ගෙවීමක් ඇතුළත් කරන්න', payment_later: 'ගෙවීම පසුව',
    cash: 'මුදල්', cheque: 'චෙක් පත',
    amount: 'මුදල', balance: 'ශේෂය', total_amount: 'මුළු මුදල',
    your_entries: 'අද මගේ එකතු කිරීම්', note: 'සටහන', note_hint: 'උදා: ඊළඟ සිකුරාදා එන්න', pay_note_hint: 'විකල්ප සටහන',
    submit: 'ඉදිරිපත් කරන්න', confirm: 'තහවුරු කරන්න', edit: 'සංස්කරණය', delete: 'මකන්න', cancel: 'අවලංගු කරන්න',
    save_note: 'සටහන සුරකින්න', cheque_no: 'චෙක් අංකය', bank: 'බැංකුව', branch: 'ශාඛාව',
    status_not_visited: 'නොගිය', status_shop_closed: 'කඩය වසා ඇත',
    status_revisit: 'නැවත එන්න', status_delivered_pending: 'බෙදා දුන් — ගෙවීම් බලාපොරොත්තු',
    status_delivered_paid: 'බෙදා දුන් සහ ගෙවූ', status_payment_submitted: 'ගෙවීම ඉදිරිපත් කළා',
    pending: 'බලාපොරොත්තු', confirmed: 'තහවුරු', rejected: 'ප්‍රතික්ෂේප',
    why_not_delivered: 'ඇයි නොලැබුණේ?', reason_required: 'හේතුව අවශ්‍යයි',
    fully_collected: 'සම්පූර්ණයෙන් එකතු', temp_balance: 'ශේෂය (ඔබේ ඇතුළත් කිරීම්)',
    your_collected: 'ඔබ එකතු කළේ', management_note: 'කාර්යාලයෙන් සටහනක්',
    combined_payment: 'ඒකාබද්ධ ගෙවීම', select_bills: 'බිල් තෝරන්න',
    filter_all: 'සියල්ල', filter_not_visited: 'නොගිය', filter_not_delivered: 'නොලැබුණු',
    filter_awaiting: 'බලාපොරොත්තු', filter_submitted: 'ඉදිරිපත් කළා',
    no_bills: 'පවරා ඇති බිල් නැත', no_payments: 'අද ගෙවීම් නැත',
    loading: 'පූරණය වෙමින්...', logout: 'ඉවත් වන්න',
  },
  ta: {
    bills: 'பில்கள்', payments: 'கொடுப்பனவுகள்', search: 'பில் அல்லது வாடிக்கையாளரைத் தேடுங்கள்...',
    delivered: 'வழங்கப்பட்டது', not_delivered: 'வழங்கப்படவில்லை',
    shop_closed: 'கடை மூடப்பட்டுள்ளது', come_later: 'பிறகு வாருங்கள்',
    add_payment: 'கொடுப்பனவு சேர்க்கவும்', payment_later: 'கொடுப்பனவு பிறகு',
    cash: 'பணம்', cheque: 'காசோலை',
    amount: 'தொகை', balance: 'இருப்பு', total_amount: 'மொத்த தொகை',
    your_entries: 'இன்றைய எனது வசூல்கள்', note: 'குறிப்பு', note_hint: 'எ.கா: அடுத்த வெள்ளி வாருங்கள்', pay_note_hint: 'விருப்பமான குறிப்பு',
    submit: 'சமர்ப்பிக்கவும்', confirm: 'உறுதிப்படுத்தவும்', edit: 'திருத்தவும்', delete: 'நீக்கவும்', cancel: 'ரத்துசெய்',
    save_note: 'குறிப்பை சேமிக்கவும்', cheque_no: 'காசோலை எண்', bank: 'வங்கி', branch: 'கிளை',
    status_not_visited: 'செல்லவில்லை', status_shop_closed: 'கடை மூடப்பட்டுள்ளது',
    status_revisit: 'மீண்டும் வாருங்கள்', status_delivered_pending: 'வழங்கப்பட்டது — கொடுப்பனவு நிலுவையில்',
    status_delivered_paid: 'வழங்கப்பட்டு கொடுத்தது', status_payment_submitted: 'கொடுப்பனவு சமர்ப்பிக்கப்பட்டது',
    pending: 'நிலுவையில்', confirmed: 'உறுதிப்படுத்தப்பட்டது', rejected: 'நிராகரிக்கப்பட்டது',
    why_not_delivered: 'ஏன் வழங்கவில்லை?', reason_required: 'காரணம் தேவை',
    fully_collected: 'முழுமையாக வசூலிக்கப்பட்டது', temp_balance: 'இருப்பு (உங்கள் உள்ளீடுகள்)',
    your_collected: 'நீங்கள் வசூலித்தது', management_note: 'அலுவலகத்திலிருந்து குறிப்பு',
    combined_payment: 'கூட்டு கொடுப்பனவு', select_bills: 'பில்களைத் தேர்ந்தெடுக்கவும்',
    filter_all: 'அனைத்தும்', filter_not_visited: 'செல்லவில்லை', filter_not_delivered: 'வழங்கவில்லை',
    filter_awaiting: 'நிலுவையில்', filter_submitted: 'சமர்ப்பிக்கப்பட்டது',
    no_bills: 'ஒதுக்கப்பட்ட பில்கள் இல்லை', no_payments: 'இன்று கொடுப்பனவுகள் இல்லை',
    loading: 'ஏற்றுகிறது...', logout: 'வெளியேறு',
  },
};

@Injectable({ providedIn: 'root' })
export class WorkerTranslateService {
  lang = signal<Lang>((localStorage.getItem('workerLang') as Lang) ?? 'en');

  set(lang: Lang): void {
    this.lang.set(lang);
    localStorage.setItem('workerLang', lang);
  }

  t(key: string): string {
    return TRANSLATIONS[this.lang()][key] ?? key;
  }
}
