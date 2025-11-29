# Daily Verse - Android App ✝️

**Daily Verse** is a minimalist, offline-first Android application designed to deliver daily spiritual inspiration. It features a clean UI, a home screen widget, and a curated collection of Bible verses with easy-to-understand explanations.

---

## 📱 Features

* **Daily Refresh:** Automatically generates a new verse every 24 hours.
* **Offline First:** All data is stored locally in JSON, requiring no internet connection.
* **Home Screen Widget:** A synchronized widget that displays the current verse and explanation directly on your home screen.
* **Smart Notifications:** Daily reminders at 8:00 AM to check your verse (powered by `WorkManager`).
* **Genre Filtering:** Filter verses by 6 categories:
    * *Encouragement & Hope*
    * *Wisdom & Guidance*
    * *Love & Relationships*
    * *Faith & Trust*
    * *Strength & Courage*
    * *Gratitude & Praise*
* **OLED Dark Mode:** A true-black theme optimized for OLED screens to save battery and reduce eye strain.
* **Verse History:** Navigate back and forth through previously generated verses using arrow controls.
* **Sharing:** Native Android sharing to send verses to friends or social media.
* **Splash Screen:** Smooth fade-in animation on startup.

---

## 🛠️ Tech Stack

* **Language:** [Kotlin](https://kotlinlang.org/)
* **UI:** XML Layouts (Material Design 3 Components)
* **Architecture:** Repository Pattern (`VerseRepository` handles data logic)
* **Local Storage:** `SharedPreferences` (State persistence) & JSON Parsing (Data source)
* **Background Tasks:** [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) (Reliable notification scheduling)
* **Widget:** `AppWidgetProvider`

---

## 📂 Project Structure

The project follows a standard Android structure. Key files include:

* **`MainActivity.kt`**: Handles UI logic, bottom sheets, and navigation.
* **`VerseRepository.kt`**: Central logic for parsing JSON, managing daily rotation, filtering genres, and updating widgets.
* **`DailyVerseWidget.kt`**: Manages the home screen widget behavior.
* **`DailyWorker.kt`**: Background worker for triggering notifications.
* **`assets/verses.json`**: The local database containing all verse data.

---

## 🚀 Getting Started

### Prerequisites
* Android Studio Iguana or newer.
* JDK 17 or newer.

### Installation
1.  **Clone the repository:**
    ```bash
    git clone [https://github.com/j-j-j-github/DAILYVERSE.git](https://github.com/j-j-j-github/DAILYVERSE.git)
    ```
2.  **Open in Android Studio.**
3.  **Sync Gradle:** Allow the project to download dependencies.
4.  **Build & Run:** Connect a device or start an emulator and hit Run.

---

## ⚙️ Configuration

### Adding New Verses
To add more content, simply edit the `app/src/main/assets/verses.json` file.
Format:
```json
{
  "id": 501,
  "text": "Verse text here...",
  "reference": "Book Chapter:Verse",
  "genre": "Faith & Trust",
  "explanation": "Simple explanation here...",
  "version": "NIV"
}
