# Local Receipt Recognition

## Supported layout rules

- WeChat Pay: large standalone signed amount; merchant title above the amount; Chinese payment date; left-label/right-value rows for product and merchant legal name.
- Alipay: aliases including 商品说明, 商家名称, 收款方, 创建时间 and 付款时间. Prefer an explicit payment date when creation and payment dates differ.
- Taobao / Tmall and Pinduoduo: prefer 实付款 / 实付金额 / 合计实付 to item prices, shipping and discounts. Store-card fallback extracts a store title and following product/SKU lines when visible.
- Text may omit colons or contain spaces within labels. Four-character labels allow one edit error only when the first two characters agree, preventing 商品名称 from being confused with 商户名称.
- OCR line coordinates reconstruct visual rows instead of trusting text-block order.
- Large monetary text can resolve smaller standalone-price candidates only when its font height is clearly dominant. Conflicting actual-payment labels remain blank.
- Unicode minus signs and full-width decimal points are normalized. Transaction IDs, card suffixes, refund-success and unpaid pages do not become confirmed spending automatically.
- Product names and numeric values are not invented from fuzzy labels. Notes stay blank. Every result remains a draft.

## Reference layouts found online

References guide field/layout rules; they are not a downloaded training dataset. Remote screenshots were not added to the project.

- [WeChat Pay official transaction-record example](https://wx.gtimg.com/pay/download/coupon/marketing_single_v1.0.pdf): amount, product, merchant and payment-time fields.
- [Alipay official bill/transaction field reference](https://help.alipay.com/support/help_detail.htm?help_id=398277).
- [Alipay merchant screenshot example](https://www.daetool.com/question/7520.html): merchant, payment amount and product description.
- [Taobao official order-detail example](https://developer.alibaba.com/docs/doc.htm?articleId=109397&docType=1&treeId=571): also illustrates deposit/balance ambiguity, which requires manual review.
- [Taobao shopper order screenshot](https://www.smartshanghai.com/articles/shopping/how-to-make-a-return-on-taobao): item price, discount and final-payment layout.
- [Pinduoduo shopper order screenshot](https://www.dcard.tw/f/buyonline/p/257621521): coupon/final-payment discrepancy; app-displayed payment still requires user review.
- [Pinduoduo order example](https://www.smzdm.com/p/60539533/): multiple discounts and actual-payment amount.

## Validation boundaries

The provided WeChat image is tested with the actual bundled Chinese OCR model in an isolated Android emulator. Four-platform field and coordinate variations are also covered by deterministic regression cases. This is not an accuracy benchmark across all versions of these apps. More real screenshots are needed for unknown layouts, truncated product names, multiple orders, installments and refunds.

The original user image is passed to the emulator explicitly and is not committed as a project asset. Diagnostic OCR output remains under ignored build/qa. The original-image test does not create ledger entries.
