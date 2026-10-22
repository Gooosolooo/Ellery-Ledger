# Physical device verification — 2026-10-22

Device: Samsung S24 Ultra, Android 16 / One UI 8.5. App: Ellery Ledger 0.2.2 (version code 6).

## Reproduced failure

The accessibility service was bound and had capability mask 129 (window content and screenshots). On the user's WeChat transaction detail page, the service retrieved the current WeChat window but found zero visible text regions. Capturing that window succeeded at 1080 × 2340. Local ML Kit OCR returned 225 characters. The displayed minus sign before the amount was recognized as the Chinese character 一, producing `一31.71`; the previous parser rejected it.

## Changes

- Keep page reading and screenshot fallback distinct. When the active root is unavailable, Android 14+ can use the actual focused application window for a screenshot. No background window is selected.
- Register window state/window list events for service window handling. Event callbacks do not read transaction content or trigger bookkeeping.
- Normalize an OCR leading 一 only when the complete line matches a two-decimal monetary amount. Do not replace this character throughout product or merchant names.
- Add the merchant short-name label and retain fuzzy label matching.
- Keep the last manual attempt's stage report and OCR text in app-private preferences for diagnosis. Clear the previous OCR text at the next attempt. No screenshot image is written by this pipeline and no data is uploaded.

## Verified on the phone

Through the real quick-settings path (expand panel, invoke tile), the current WeChat page generated a new draft:

- Amount: 31.71 (matches the user's stated amount).
- Merchant: 淘宝特价版平台商户.
- Date: 2026-10-22.
- Product and note: empty for user completion.
- Status: draft. Confirmed entry count remained 1; draft count increased from 0 to 1.
- The Android notification service contained a high-importance pending-review notification tagged with the new draft's UUID.

No bookkeeping confirmation was performed, and the app was updated without uninstalling or clearing its data.

## Second live transaction

At 21:33 local time, the user switched to a different WeChat transaction and stated the displayed amount was 28.00. The quick-settings tile captured the new WeChat window (3813, compared with the first transaction's 3785). Local OCR returned 294 characters and created a distinct draft with amount 28, date 2026-10-21, merchant 潮乡人粿果面(大源店), and a product description requiring manual review. The previous draft remained 31.71. Draft count increased from 1 to 2; confirmed count remained 1. The second draft UUID was 946d5416-6920-4ae3-bfe8-3c9961a2546a. No confirmation or image-file save was performed.

## Automated checks

89 business checks passed, including the misread minus sign, a different corrected amount, conflicts between multiple amounts, ordinary product text, and refund rejection. Debug build and Android lint passed.

## Remaining coverage

Live Pinduoduo/Alipay/Taobao pages and Samsung clipboard-image compatibility have not yet been verified by this report. Two different WeChat transaction amounts have been verified on the physical device. The supplied screenshot is not used as the source of live capture.
