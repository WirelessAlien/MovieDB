<div align="center">
  <a href="https://github.com/WirelessAlien/MovieDb">
    <img src="https://github.com/WirelessAlien/MovieDB/blob/master/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="ShowCase Logo" width="100" height="100">
  </a>
  <h1>ShowCase</h1>
  <p>Explore and organize your personal collection of films and television series with ease.</p>
</div>

<!-- Badges -->
<div align="center">
  <a href="https://github.com/WirelessAlien/MovieDB/actions/workflows/release.yml">
    <img src="https://github.com/WirelessAlien/MovieDB/actions/workflows/release.yml/badge.svg" alt="Build Status"/>
  </a>
  <a href="https://f-droid.org/packages/com.wirelessalien.android.moviedb/">
    <img src="https://img.shields.io/f-droid/v/com.wirelessalien.android.moviedb.svg" alt="F-Droid"/>
  </a>
  <a href="https://github.com/WirelessAlien/MovieDB/releases/latest">
    <img src="https://img.shields.io/github/v/release/WirelessAlien/MovieDB?label=GitHub" alt="GitHub release"/>
  </a>
  <a href="https://www.gnu.org/licenses/gpl-3.0.txt">
    <img src="https://img.shields.io/github/license/WirelessAlien/MovieDB" alt="License"/>
  </a>
</div>

<p align="center">
  <a href="#about-the-project">About</a> •
  <a href="#key-features">Features</a> •
  <a href="#screenshots">Screenshots</a> •
  <a href="#installation">Installation</a> •
  <a href="#building-from-source">Build</a> •
  <a href="#contributing">Contribute</a> •
  <a href="#donate">Donate</a> •
  <a href="#license">License</a>
</p>

---

## About The Project

ShowCase (formerly Movie DB) is a fully open-source Android application designed for enthusiasts to explore, discover, and organize their personal collection of films and television series.

It offers synchronization with your TMDB and Trakt accounts. Once logged in, you can sync your favorites, watchlist, rated movies, and more. It also integrates with a local database to keep track of your shows. You can assign various statuses to each title—such as ‘planned’, ‘watched’, or ‘dropped’—rate them, and note the dates you commenced and concluded each show.

The app allows you to filter shows based on genre, release dates, and more. Detailed information on each show, including the cast, crew, and recommendations for similar titles, is also available.

---

## Key Features

-   ✨ **TMDB & Trakt Sync:** Seamless synchronization for favorites, watchlist, ratings, and collections.
-   📝 **List Management:** Create and manage public/private lists on TMDB.
-   📊 **External Ratings:** View ratings from IMDb and other sources.
-   📱 **Offline Support:** Local database for tracking shows and personal progress.
-   🔄 **Data Management:** Import and export your local database.
-   🎨 **Material You:** Modern UI that adapts to your device's theme.
-   🔔 **Release Notifications:** Get notified about new movie and TV show releases.
-   🔄 **External Data Import:** Import data from other app (more on [Wiki](https://github.com/WirelessAlien/MovieDB/wiki/Frequently-Asked-Questions-(FAQ)#can-i-import-a-database-from-another-application))
-   ...and many more!
---

## Screenshots

<pre>
<img src="https://github.com/user-attachments/assets/ab547d5e-a1ca-4b72-a80d-4414cf68b38b" width="130" height="280" /> <img src="https://github.com/user-attachments/assets/a30a0917-012f-40af-a882-2f1839a40076" width="130" height="280" /> <img src="https://github.com/user-attachments/assets/79f3a547-e66a-4f5d-bdba-8a1ed807df39" width="130" height="280" /> <img src="https://github.com/user-attachments/assets/1cafa7af-f725-424a-8ee2-58434d5c95fa" width="130" height="280" /> <img src="https://github.com/user-attachments/assets/61d0e36c-47d8-4668-a2ea-cdb7a247fee9" width="130" height="280" /> <img src="https://github.com/user-attachments/assets/6830be73-220b-4b37-9083-5e7de5e07300" width="130" height="280" />
</pre>

---

## Installation

### Release Channels

You can download and install ShowCase from the following sources:

| Source            | Link                                                                                                                                               | Notes                                                     |
| :---------------- | :------------------------------------------------------------------------------------------------------------------------------------------------- | :-------------------------------------------------------- |
| **GitHub Releases** | [<img src="https://img.shields.io/github/v/release/WirelessAlien/MovieDB?label=Latest%20Release&style=for-the-badge" alt="GitHub release"/>](https://github.com/WirelessAlien/MovieDB/releases/latest) | Standard version & ShowCase Plus (with GDrive backup)     |
| **F-Droid**          | [<img src="https://img.shields.io/f-droid/v/com.wirelessalien.android.moviedb.svg?style=for-the-badge" alt="F-Droid"/>](https://f-droid.org/packages/com.wirelessalien.android.moviedb/) | Standard version, no GDrive backup                        |
| **Google Play**      | [<img src="https://img.shields.io/badge/Play%20Store-Download-black?style=for-the-badge&logo=googleplay" alt="Play Store"/>](https://play.google.com/store/apps/details?id=com.wirelessalien.android.moviedb.full) | Ads supported version with IAP (supports the developer)                     |


> [!IMPORTANT]
> The **ShowCase Plus** version includes a feature for backing up your local database to Google Drive. This version is available exclusively from the [GitHub Releases](https://github.com/WirelessAlien/MovieDB/releases/latest) page and is not available on F-Droid due to its inclusion of proprietary Google services.  
>
> The **Google Play** version is a **Ads supported version with IAP** of the app. The same app is available for free on GitHub Release — purchasing it on Play Store is simply an additional way to **support the developer**.  
>
> Learn more about the different app versions on the [project wiki](https://github.com/WirelessAlien/MovieDB/wiki/App-Version).


---

## Building from Source

ShowCase can be compiled using Android Studio or via the command line with Gradle.

1.  **Clone the repository**
  
2.  **Set up API Keys:**
    *   Get a free API Key from [TMDB](https://www.themoviedb.org/settings/api).
    *   Create a `config.properties` file in the root project directory. If a `config.properties` file exists,
    *   Add your keys to `config.properties`:
        ```properties
        api_read_access_token="YOUR_TMDB_READ_ACCESS_TOKEN"
        api_key="YOUR_TMDB_API_KEY"
        ```
3.  **Build the application:**
    *   **Using Android Studio:**
        *   Open Android Studio and select 'Open an existing Android Studio project' and use the cloned repository or directly clone the repository using VCS `https://github.com/WirelessAlien/MovieDB.git`.
        *   Let Gradle sync and download dependencies.
        *   Build the project using `Build > ` or run it on an emulator/device using `Run > Run 'app'`.
    *   **Using Command Line (Gradle Wrapper):**
        ```bash
        # For Linux/macOS
        ./gradlew assembleDebug  # For debug build
        ./gradlew assembleRelease # For release build (requires signing configuration)

        # For Windows
        gradlew.bat assembleDebug
        gradlew.bat assembleRelease
        ```

---

## Contributing

Contributions are what make the open-source community such an amazing place to learn, inspire, and create. Any contributions you make are **greatly appreciated**!

### Reporting Bugs & Requesting Features

If you encounter a bug or have a feature idea, please check the [existing issues](https://github.com/WirelessAlien/MovieDB/issues) first. If your issue or idea isn't listed, feel free to [open a new one](https://github.com/WirelessAlien/MovieDB/issues/new/choose):
*   [Report a Bug](https://github.com/WirelessAlien/MovieDB/issues/new?assignees=&labels=bug&template=bug_report.md&title=)
*   [Request a Feature](https://github.com/WirelessAlien/MovieDB/issues/new?assignees=&labels=enhancement&template=feature_request.md&title=)

### Pull Requests

1.  Fork the Project.
2.  Create your Feature Branch (`git checkout -b feature/AmazingFeature`).
3.  Commit your Changes (`git commit -m 'Add some AmazingFeature'`).
4.  Push to the Branch (`git push origin feature/AmazingFeature`).
5.  Open a Pull Request against the `master` branch.

Please ensure your code adheres to the project's coding standards and that any new features are well-tested.

### Translations

Help us make ShowCase accessible to more users by contributing translations!
Visit our [Weblate project page](https://hosted.weblate.org/engage/showcase/) to get started.

<a href="https://hosted.weblate.org/engage/showcase/">
<img src="https://hosted.weblate.org/widget/showcase/strings/287x66-white.png" alt="Translation status" />
</a>


---

## License

Distributed under the GNU General Public License v3.0. See [`LICENSE`](LICENSE) file for the full text and [GPL-3.0 Overview](https://www.gnu.org/licenses/gpl-3.0.html) for more information.

---

## Donate

If you find ShowCase useful and would like to support its development, please consider donating. Your support helps keep the project alive.

<noscript><a href="https://liberapay.com/WirelessAlien/donate"><img alt="Donate using Liberapay" src="https://liberapay.com/assets/widgets/donate.svg"></a></noscript>  

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/I2I01JN1GG)

<a href="https://www.paypal.me/WirelessAlien">
  <img src="https://github.com/user-attachments/assets/d2b47113-80e3-40f7-aeb1-a4e07c56c2ef" alt="paypal" width="100" />
</a>
