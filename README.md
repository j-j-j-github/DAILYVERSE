# Daily Verse - Android App ✝️

**Daily Verse** is a minimalist, offline-first Android application designed to deliver daily spiritual inspiration. It features a clean UI, a synchronized home screen widget, and a curated collection of Bible verses with easy-to-understand explanations.

---

## 📱 Features

- **Daily Refresh**: Automatically generates a new verse every 24 hours.  
- **Offline First**: All data is stored locally in JSON, requiring no internet connection.  
- **Home Screen Widget**: Displays the current verse reference.  
- **Instant Updates**: Automatically refreshes at midnight using exact alarms, regardless of battery optimization.  
- **Customizable Themes**: Choose from multiple color themes (White, Black, Blue, Green, Purple) to match your wallpaper.  
- **App Guide (Tutorial)**: Beautiful, animated onboarding experience for first-time users, also accessible anytime from Settings.  
- **Smart Notifications**: Daily reminders at 8:00 AM to check your verse.  
- **Genre Filtering**: Filter verses by 6 categories:  
  - Encouragement & Hope  
  - Wisdom & Guidance  
  - Love & Relationships  
  - Faith & Trust  
  - Strength & Courage  
  - Gratitude & Praise  
- **OLED Dark Mode**: True-black theme optimized for OLED screens to save battery and reduce eye strain.  
- **Verse History**: Navigate back and forth through previously generated verses using arrow controls.  
- **Sharing**: Native Android sharing to send verses to friends or social media.  
- **Smooth Animations**: Includes a fade-in splash screen and polished UI transitions.  

---

## 🛠️ Tech Stack

- **Language**: Kotlin  
- **UI**: XML Layouts (Material Design 3 Components), NestedScrollView for responsive settings  
- **Architecture**: Repository Pattern (`VerseRepository` handles data logic)  
- **Local Storage**: SharedPreferences (state persistence) & JSON parsing (data source)  
- **Background Tasks**:  
  - WorkManager for reliable daily notifications  
  - AlarmManager & BroadcastReceiver for precise midnight widget updates  
- **Widget**: AppWidgetProvider with RemoteViews  

---

## 📂 Project Structure

Key files and responsibilities:

- **MainActivity.kt**: Handles UI logic, splash screen animation, app tutorial, and settings.  
- **VerseRepository.kt**: Central logic for parsing JSON, managing daily rotation, filtering genres, and widget state.  
- **DailyVerseWidget.kt**: Manages the home screen widget behavior.  
- **WidgetUpdateReceiver.kt**: BroadcastReceiver that handles "Instant" midnight updates and system time changes.  
- **DailyWorker.kt**: Background worker for triggering notifications.  
- **assets/verses.json**: Local database containing all verse data.  

---

**Daily Verse** delivers inspiration right to your home screen every day, even offline, with a smooth, modern Android experience.
