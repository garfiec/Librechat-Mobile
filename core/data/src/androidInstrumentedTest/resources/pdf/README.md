# PDF normalizer fixtures

Synthetic: one line of Helvetica text ("Switchboard fixture: photosynthesis converts light into
chemical energy."). Never replace these with real user documents. `PdfKitPdfNormalizerTest` (iosTest)
inlines the same files as base64; regenerate both together.

`plain.pdf` is a hand-written one-page PDF whose xref was rebuilt by `qpdf raw.pdf plain.pdf`. The
rest derive from it with qpdf 12:

```bash
# Copy-protected, the shape of InDesign publisher exports: RC4-128, R=3, empty user password
qpdf --allow-weak-crypto --encrypt "" ownersecret 128 \
     --print=full --modify=none --extract=n --use-aes=n -- plain.pdf rc4_128_empty_user.pdf
# AES-128, R=4, empty user password
qpdf --encrypt "" ownersecret 128 \
     --print=full --modify=none --extract=n --use-aes=y -- plain.pdf aes_128_empty_user.pdf
# Modern variant: AES-256, R=6, empty user password
qpdf --encrypt "" ownersecret 256 \
     --print=full --modify=none --extract=n -- plain.pdf aes_256_empty_user.pdf
# Genuinely password-protected (user password "userpw"): decrypted only with the typed password
qpdf --allow-weak-crypto --encrypt "userpw" ownersecret 128 \
     --use-aes=n -- plain.pdf real_password.pdf
qpdf --encrypt "userpw" ownersecret 256 -- plain.pdf aes_256_real_password.pdf
```

`corrupt.pdf` is not made with qpdf: it is a `%PDF-` header and an `/Encrypt` key followed by
random bytes.
