# Verification

## Sort arrow appearance (2026-10-25, source only)

- Date and price buttons now use the ⇋ glyph rotated clockwise by 90 degrees, clipped into independently colored halves. Ascending highlights the right/up half; descending highlights the left/down half; inactive halves are gray.
- Both inactive labels include 升/降序. Light button backgrounds keep blue and gray directions distinguishable; text and accessibility descriptions retain the selected order.
- Source compilation and Android Lint verified. Native glyph appearance, clipping, button fit and direction colors still need a device check after building in Android Studio; no APK generated or installed.

## Independent date / price sorting (2026-10-25, source only)

- Added 51 checks for independent date/price ascending/descending order, mutually exclusive active modes, numeric decimal comparison (including different decimal scales), date-descending tie breaks, stable same-date/same-price ordering and unchanged source records.
- Covered sorting after combined date/category/keyword filters, retaining sort order when filters change, unchanged count/total, draft exclusion, empty results and saved-state value restoration with backward-compatible defaults.
- Current core suite passed: 87 parser checks, 14 source-selection checks, 42 filter checks, 51 sort checks and 43 recognition-draft checks (237 total).
- Java source compilation and Android Lint passed (0 errors; warnings concern existing platform/resources/source-panel issues). No APK was generated or installed.
- Device checks pending: two separate button positions, active labels/highlights, tapping to toggle order, filter/reset interaction and returning from editing or activity recreation.

## Ledger filters (2026-10-23, source only)

- Updated 42 synthetic-data checks for confirmed-only lists, day/month filtering, combined category/keyword matching, custom/empty categories, literal search punctuation, English case folding, stable date ordering, nonmutation of source records, empty results and reset criteria.
- Covered exact decimal totals, drafts excluded from lists/search/counts/totals (including empty or invalid amounts), leap days and the transition from pending to explicitly confirmed.
- Added 43 recognition-draft checks for empty results, OCR failure details, blank user notes, stable/fresh record identifiers and current-attempt accessible-text fallback when screenshot parsing omits fields.
- Core suite: 89 business checks, 14 source-selection checks, 42 ledger-filter checks and 43 recognition-draft checks passed (188 total).
- Application and Android test Java source compilation and Android Lint passed. No APK was generated or installed for this change.
- Device checks pending: empty-image/failed-OCR notification and saved draft; screenshot-denial fallback; page-switch/own-app cancellation; manual completion and confirmation moving a draft into the confirmed-only ledger.
- Native wheel scrolling, dialog placement, keyboard interaction and state restoration still require emulator/device UI verification after the user builds the app in Android Studio.

Verified on 2026-10-22.

- JDK business tests: 60 checks passed, including explicit payment amounts, standalone amount candidates, conflicting amounts, missing merchant/product, manual notes and amount-only mode.
- APK compilation: Android Gradle Plugin 9.4.1 / Gradle 9.6.0 / SDK 37, target API 35.
- Android Lint passed with no errors. Remaining warnings include compatibility/styling suggestions for the chosen target API, API-specific backup/accessibility metadata, the native Switch widget and a newer available Gradle version.
- Android API 37 emulator integration passed: bundled Chinese OCR, draft/confirmed state, repeated confirmation, stable-ID backup merge, JSON round-trip and Excel round-trip.
- Original workbook read-only test: 533 valid rows; skipped row numbers 5, 36, 242 and 299. The original file was never modified. Valid entries survived an in-memory Excel export/import with identical dates, items, amounts and notes.
- Independent openpyxl read of a synthetic Excel test fixture verified typed dates, numeric amounts, Unicode, XML special characters and multiline notes.
- Final merged application manifest contains no INTERNET permission.
- Emulator home screen launched successfully and was inspected for clipping.

## Not yet verified

- Actual S24 Ultra / One UI 8.5 shortcut capture timing, system restrictions and heads-up notification behavior.
- OCR accuracy or field extraction accuracy on the user's actual payment screenshots.
- Samsung restricted-settings consent flow for a sideloaded accessibility service.

Start with the in-app image picker or image share flow to test OCR without enabling accessibility. The amount-only switch affects future recognition tasks; every result still requires manual confirmation.

## Version 0.1.2 receipt recognition

- Actual user-provided WeChat JPEG (1080 x 2340) ran through the bundled Chinese model and spatial parser in an isolated emulator. Result: amount 192.50, date 2026-10-22, merchant cdf中免, product 雪肌精谧雅臻活乳液盈润型140ml, empty note and draft status. Amount-only mode also passed.
- Original-image tests do not insert ledger records. The 60 business checks include WeChat/Alipay field variants, Taobao/Pinduoduo item-price versus paid-amount cases, label OCR typos, column coordinates, refunds and unpaid status.
- Online references and limits are listed in RECOGNITION.md. Cross-platform tests are representative rule cases, not a claim that every remote screenshot or app version has been tested.
- Fixed the previous onCreate/finish/onStop capture scheduling bug and renamed the action tile to 识别当前账单, with non-toggleable tile metadata.

## Version 0.2.0 blue UI

- Reference inspected: local FocusBuddy Ui.java, MainActivity.java and AppDialog.java. Applied its 18dp rounded cards, compact top tabs and spacious controls with a blue palette. The reference project was not modified.
- Overview, draft editor and settings rendered and visually inspected in the emulator. The actual provided-image draft appears with 192.50, cdf中免 and the recognized product.
- Final APK manifest verified as versionName 0.2.0 / versionCode 4. Build and Android Lint passed; 60 parser checks passed; original-image offline OCR and amount-only extraction passed against the final target APK.
- Draft UI flow was exercised by sharing the original image into the emulator app. No draft was confirmed.

## Quick Settings correction (source-only)

- Official Android documentation rechecked: onClick can directly trigger background work; startActivityAndCollapse explicitly starts an Activity.
- Removed CaptureActivity and the normal click path that launched it. An already-connected accessibility service now dismisses the shade with GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE and schedules screenshot recognition directly.
- A missing service shows a Quick Settings dialog; only the user's explicit choice opens system accessibility settings. No silent redirect to MainActivity.
- Added the official requestAddTileService entry in app settings and accurate already-added / not-added feedback.
- This correction has not been installed or verified on the user's Android 16 phone. Previous OCR tests do not establish Quick Settings behavior. No APK is generated for this source-only change.

## Home tab swipe navigation (source-only)

- Added four persistent ViewPager pages in overview, drafts, all entries and settings order. Tab clicks and swipes share the selected state; page ends do not wrap.
- Each page retains its vertical scroll position during tab changes; all-entry filter controls retain their input. Overview and settings recognition switches stay synchronized.
- The entry editor remains outside the pager.
- Device checks pending: slow drag and snap-back; fast fling; first/last boundaries; vertical scrolling and card taps; horizontal tag scrolling; search selection and keyboard; draft editor isolation; filter retention and recognition switch synchronization.
