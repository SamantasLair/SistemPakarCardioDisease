# Sistem Pakar Diagnosis Risiko Penyakit Jantung (Cardio Expert System)

**Sistem Pakar Diagnosis Penyakit Jantung** adalah aplikasi perangkat lunak yang dikembangkan sebagai Tugas Akhir mata kuliah Sistem Pakar. Aplikasi ini bertujuan untuk melakukan deteksi dini risiko penyakit kardiovaskular dengan mengintegrasikan basis pengetahuan medis standar internasional (WHO & AHA) dan analisis statistik berbasis data (*data-driven logic*).

Sistem ini menggabungkan pendekatan *Hybrid Fuzzy-Logic* (Model Sugeno) dengan aturan inferensi klinis (*Rule-Based Reasoning*) untuk memberikan hasil diagnosis yang akurat dan dapat dipertanggungjawabkan secara medis.

---

## 📋 Metodologi & Algoritma

Sistem ini dibangun menggunakan pendekatan hibrida yang kompleks untuk memastikan presisi diagnosis:

### 1. Hybrid Fuzzy Inference System (Sugeno Model)
Mesin inferensi utama (`InferenceEngine.java`) menggunakan logika fuzzy untuk menangani ketidakpastian data pasien.
* **Variabel Kontinu (Umur, Tekanan Darah, BMI):** Dihitung menggunakan **Fungsi Keanggotaan Gaussian** (Gaussian Membership Function). Nilai *mean* ($\mu$) dan *standar deviasi* ($\sigma$) diambil secara dinamis dari pelatihan dataset.
* **Variabel Diskrit (Kolesterol, Glukosa):** Menggunakan logika *Crisp* dengan pemetaan kategori risiko bertingkat.

### 2. Pearson Correlation Weighting
Bobot pengaruh setiap gejala terhadap penyakit jantung tidak ditentukan secara sembarang, melainkan melalui proses "Pelatihan" (`TrainingService.java`). Sistem menghitung **Korelasi Pearson** antara setiap atribut gejala dengan kejadian penyakit jantung dalam dataset historis. Nilai korelasi ini menjadi faktor pengali ($w$) dalam penentuan skor akhir.

### 3. Rule-Based Reasoning (Clinical Guidelines)
Selain perhitungan statistik, sistem menerapkan aturan medis mutlak (*Hard Rules*) yang diadopsi dari pedoman global:
* **ACC/AHA 2017 Guidelines:** Untuk klasifikasi Hipertensi (Elevated, Stage 1, Stage 2, Hypertensive Crisis).
* **WHO Standards:** Untuk klasifikasi Indeks Massa Tubuh (Overweight & Obesity).
* **Synergistic Rules:** Logika khusus untuk mendeteksi kombinasi gejala berbahaya (contoh: Perokok + Hipertensi) yang meningkatkan skor risiko secara eksponensial.

---

## 🧮 Kalkulasi Teknis

Berikut adalah rincian formulasi matematis yang diimplementasikan dalam sistem:

| Jenis Perhitungan | Deskripsi Formula |
| :--- | :--- |
| **Gaussian Membership** | $\mu(x) = e^{-0.5 \left(\frac{x - \bar{x}}{\sigma}\right)^2}$ <br> *Digunakan untuk menilai derajat keanggotaan variabel kontinu.* |
| **Body Mass Index (BMI)** | $BMI = \frac{Berat (kg)}{Tinggi (m)^2}$ |
| **Scoring Model** | $Risk = \frac{\sum (\alpha_i \times w_i)}{\sum w_{total}} \times 100\%$ <br> Dimana $\alpha$ adalah derajat risiko (0-1) dan $w$ adalah bobot korelasi atribut. |
| **Evaluasi Model** | Menghitung *Confusion Matrix* (TP, TN, FP, FN) untuk menghasilkan akurasi, presisi, *recall*, dan F1-Score. |

---

## 🎯 Tujuan Pengembangan

1.  **Implementasi Kecerdasan Buatan:** Menerapkan algoritma Sistem Pakar dan Logika Fuzzy dalam kasus nyata kesehatan.
2.  **Deteksi Dini:** Menyediakan alat bantu *screening* awal bagi masyarakat untuk mengetahui tingkat risiko penyakit jantung sebelum berkonsultasi ke dokter.
3.  **Validasi Medis:** Menguji akurasi prediksi sistem komputerisasi dibandingkan dengan diagnosis aktual dalam dataset medis.

---

## 💎 Manfaat

* **Bagi Tenaga Medis:** Sebagai *Second Opinion Support System* yang dapat membantu mempercepat proses triase pasien berdasarkan tingkat risiko.
* **Bagi Pengguna Awam:** Meningkatkan kesadaran (*awareness*) akan pentingnya parameter kesehatan seperti tekanan darah dan kadar gula melalui rekomendasi yang personal.
* **Edukasi:** Memberikan saran gaya hidup (*medical advice*) yang spesifik berdasarkan anomali parameter yang terdeteksi (misal: saran diet untuk kolesterol tinggi).

---

## 🛠️ Arsitektur Sistem

* **Bahasa Pemrograman:** Java (JDK 17+)
* **Antarmuka Pengguna:** JavaFX (FXML)
* **Basis Data:** H2 Database Engine (Embedded)
* **Analisis Data:** Weka Library (untuk kalkulasi statistik tingkat lanjut) & Custom Statistical Engine.
* **Pattern:** MVC (Model-View-Controller)

---

> *"Prevention is better than cure."* — Aplikasi ini dirancang untuk mendukung upaya preventif melalui teknologi yang presisi.
