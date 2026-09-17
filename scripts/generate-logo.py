"""Regenerate the canonical vector mark. Requires only Python 3 standard library."""
from pathlib import Path
root = Path(__file__).resolve().parent.parent
r = 'M30 27H57Q77 27 77 45Q77 58 63 62L78 81H62L47 62H44V81H30ZM44 40V50H56Q64 50 64 45Q64 40 56 40Z'
c = 'M66 68L72 74L87 58L93 64L72 87L60 75Z'
(root/'branding/rollora.svg').write_text(f'<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 108 108"><rect width="108" height="108" rx="26" fill="#202230"/><path fill="#919cff" d="{r}"/><path fill="#75e3c0" d="{c}"/></svg>\n')
(root/'app/src/main/res/drawable/ic_mark.xml').write_text(f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108"><path android:fillColor="#919CFF" android:pathData="{r}"/><path android:fillColor="#75E3C0" android:pathData="{c}"/></vector>\n')
(root/'app/src/main/res/drawable/ic_mark_mono.xml').write_text(f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108"><path android:fillColor="#FF000000" android:pathData="{r}"/><path android:fillColor="#FF000000" android:pathData="{c}"/></vector>\n')
print('Generated SVG, Android VectorDrawable and monochrome adaptive-icon layer.')
