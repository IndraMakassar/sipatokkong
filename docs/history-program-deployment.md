# Riwayat lengkap dan pengelolaan program

Perubahan ini bergantung pada PR #21 (`0015`/`0016` dan routing setoran).
Gabungkan PR #21 lebih dahulu, lalu tinjau PR ini terhadap `master` yang sudah memuatnya.
Tidak ada perubahan akses untuk pergantian pendamping.

## Urutan pemasangan

1. Pastikan skema sebelumnya sudah tersedia, terutama `0012` (jadwal/pengingat),
   `0014` (evaluasi), serta `0015` dan `0016` (notifikasi). Pengguna melaporkan
   `0015` dan `0016` berhasil dijalankan pada 25 Oktober 2026 dan menampilkan kedua
   trigger notifikasi. Agent tidak menerapkan atau mengaudit ulang database produksi.
2. Terapkan seluruh `supabase/migrations/0017_history_program_management.sql`
   dalam satu eksekusi. File sudah memiliki `BEGIN`/`COMMIT`: perubahan skema,
   fungsi, dan aturan tulis harus berhasil bersama. Jangan menjalankan potongannya
   secara terpisah. Migrasi ini hanya dijalankan sekali; jika sudah berhasil,
   jangan diulang karena kolom/fungsi sudah ada.
3. Setelah sukses, pasang APK terbaru untuk orang tua dan relawan. Tanpa migrasi,
   riwayat baru/tunggakan lama akan menampilkan error karena RPC belum tersedia.
   APK lama masih dapat membuat program, mengirim setoran valid, memberi evaluasi,
   dan menghentikan program. Tampilan status pembatalan serta riwayat lintas versi
   memerlukan APK baru.
4. Uji dengan akun/data pengujian yang diizinkan: setoran lama bisa dibuka, filter
   tanggal dan Muat lebih banyak bekerja, edit program berjalan menghasilkan
   pengganti mulai besok, pembatalan tidak menerima setoran, serta notifikasi
   setoran/evaluasi masih mengarah ke tanggal yang benar.

SQL Editor tidak mengisi riwayat Supabase CLI. Jika nantinya menggunakan `db push`
atau GitHub Actions, cocokkan skema dan catat migrasi yang benar-benar sudah
terpasang sebelum mengaktifkan deployment otomatis. Jangan menjalankan ulang
migrasi lama untuk memperbaiki perbedaan catatan migrasi.

## Perilaku penting

- Riwayat memakai `tugas_penyelesaian` langsung, dengan filter tanggal tugas
  inklusif dan urutan `(occurrence_date DESC, id DESC)`. RPC mengambil 51 baris;
  aplikasi menampilkan 50 dan menggunakan satu baris sebagai penanda halaman lanjut.
- Tunggakan lebih dari 30 hari memakai jadwal yang sama dengan pengingat dan
  mengecualikan setoran yang sudah ada. Urutannya tanggal paling lama, lalu ID tugas.
  Progres mingguan tidak mengambil data dari daftar tunggakan atau riwayat.
- Program berjalan diganti mulai besok menurut `Asia/Makassar`. Versi sebelumnya
  tetap menyimpan isi/media, bukti dan evaluasi; tunggakan sebelumnya tetap berlaku.
  Edit isi saja mempertahankan fase interval setiap N hari. Perubahan pola memakai
  awal versi baru. Program tanpa jadwal baru dalam tanggal selesai ditolak.
- Program yang belum mulai dan belum disetor dapat diedit langsung. Tugas sekali
  jalan hanya bisa diedit/dibatalkan sebelum memiliki setoran. Jenis sekali jalan
  tidak dapat diubah menjadi berulang atau sebaliknya melalui Edit.
- Penyimpanan memeriksa `revision` serta mengunci tugas, termasuk saat insert
  setoran. Request edit mempunyai UUID agar retry setelah respons hilang tidak
  membuat versi/notifikasi ganda. Jika request lama sudah tersimpan tetapi pengguna
  mengubah isian, buka ulang Edit dari daftar untuk membuat perubahan berikutnya.
- Tidak ada penghapusan bukti atau media lama. Media yang belum diganti digunakan
  kembali; foto pengganti dikompresi memakai helper aplikasi. Jalur video yang sudah
  ada tetap digunakan; pekerjaan kompresi video menyeluruh bukan bagian perubahan ini.
- Penanda pembatalan mematikan pengingat. Edit langsung tidak menambah jenis
  notifikasi baru; versi pengganti menghasilkan satu `tugas_baru` melalui trigger
  yang sudah ada. Trigger setoran/evaluasi tetap dipakai.

## Verifikasi lokal

```sh
JAVA_HOME='/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home' \
  ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.antoniusindra.sipatokkong.test/androidx.test.runner.AndroidJUnitRunner
```

Tes SQL harus memakai cluster PostgreSQL lokal terisolasi dan database baru bernama
`sipatokkong_flow_test`; tes membuat stand-in Auth/Storage serta role `authenticated`
dan `anon`. Jangan jalankan pada Supabase atau cluster yang menampung data pengguna.

```sh
psql -X -h 127.0.0.1 -p 55440 -d sipatokkong_flow_test \
  -v ON_ERROR_STOP=1 -f supabase/tests/history_program_management.sql
python3 supabase/tests/program_concurrency.py
```

Tes UI baru menggunakan HTTP engine dalam memori. Tes JVM menggunakan server HTTP
lokal. Tes SQL melibatkan RLS, trigger asli proyek, serta dua koneksi nyata untuk
menguji kedua urutan benturan setoran dengan pembatalan dan edit tanggal. Ini bukan
bukti pengiriman FCM produksi, perangkat fisik, atau upload video baru.

## Jika pemasangan gagal

Kegagalan SQL membatalkan transaksi; periksa penyebabnya sebelum mencoba lagi.
Setelah migrasi berhasil dan ada versi/setoran baru, jangan menghapus kolom atau
menggabungkan kembali versi secara manual. Tahan distribusi APK dan perbaiki lewat
migrasi lanjutan. Data versi serta setoran harus tetap dipertahankan.
