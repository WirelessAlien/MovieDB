# Frequently Asked Questions (FAQ)

### Why am I not receiving notifications for saved media?

Some device manufacturers, such as Xiaomi, have strict policies that restrict applications from running in the background to conserve power. To ensure you receive notifications, you may need to adjust your device's settings. Please try the following:

*   **Remove battery restrictions:** Find the application in your device's settings and disable any battery optimization or restriction features.
*   **Enable auto-start:** Allow the application to start automatically with your device.

These settings are usually found under "Battery", "Apps", or "Permissions" in your device's settings menu.

### Can I import a database from another application?

Yes, our application includes a powerful and flexible universal CSV importer that allows you to bring in your data from other applications.

**Key Features:**

*   **File and Delimiter:** You can select any CSV file from your device and specify the character (like a comma or semicolon) that separates the data fields.
*   **Smart Header Mapping:** The importer automatically reads the columns from your CSV file. It intelligently tries to match your columns to the app's data fields (like 'Title', 'Rating', 'Release Date', etc.).
*   **Full Control:** You have full control to review and change these automatic mappings. You can manually assign each of your CSV columns to the correct field within the app, or choose to ignore certain columns entirely.
*   **Comprehensive Data Support:** The importer supports a wide range of data fields, including titles, summaries, ratings, release dates, genres, watch status, and even links to posters. For TV shows, you can also import season and episode details.
*   **Background Importing:** The import process runs in the background, so you can start an import and continue using the app without waiting for it to finish.

To use the feature, look for the CSV import option in the **Saved** tab menu within the app. You'll be guided through selecting your file and mapping your data. This is newly added feature so if you have any trouble, please don't hesitate to report the issue to us!

### How can I mark an episode as watched?

There are several ways to mark episodes as watched, allowing you to choose the most convenient method for you. When you mark an episode, the watched date is automatically added.

**1. Quick Access (for TV Shows):**

*   Navigate to the **"Saved"** tab.
*   **Long-press** on a show to bring up a bottom sheet menu to mark any episode as watched.
*   when you go to show's **Details page**, you will see the current or next upcoming episode displayed prominently. You can simply click on it to quickly mark any episode as watched.

**2. From the Details Page:**

*   On a show's **Details page**, tap the **Edit/Save** button.
*   This will reveal options to mark individual episodes or entire seasons as watched.

**Editing Watched Details:**

For more detailed tracking, you can click **"Show More"** below the current/upcoming episode. This will allow you to edit the specific **watched date**, add a personal **rating**, and write a **review**.

### Will the data (like newly aired episodes) for my saved shows be automatically updated?

Yes, You can enable a setting in the app's **Settings** to automatically add newly aired episodes to the shows you have saved. This helps you keep your library up-to-date without manual searching.

### Can I receive app updates within the app?

Yes, You can opt-in to receive app updates directly within the application through the app's **Settings**. When a new version is available on GitHub, the app will notify you and allow you to download and install it.

### Which sync provider should I use?

The app offers several sync providers to manage your data, each with different features. Here are our recommendations:

*   **Local Only (Recommended):**
    This option provides the most features, storing all your data directly on your device. It's fast, private, and gives you access to all functionalities, including detailed episode tracking. You can choose to use the **ShowCase Plus** variant of the app to backup local database to Google Drive.

*   **Local with TMDB (Recommended):**
    This is a great hybrid approach. You can use all the local features for detailed tracking while syncing your **watchlist, favourites, ratings, and custom lists** with your TMDB account.
    *   **Important Note:** Marking a show/episode as "watched" and personal reviews are *not* synced to TMDB, as their API does not support this. This data will remain local to your device.

*   **TMDB Only (Recommended):**
    If you primarily manage your collection on TMDB, you can use this option. However, you will not have access to the app's more advanced local features like marking items as watched or writing detailed reviews.

*   **Trakt Only (Recommended):**
    This is another excellent option for syncing your data with a powerful online service.

*   **TMDB and Trakt Combination and Local and Trakt Combination (Not Recommended):**
    We strongly advise against using both TMDB and Trakt and Local and Trakt as your primary sync providers simultaneously. Managing two separate sync sources can lead to confusion and data inconsistencies.
