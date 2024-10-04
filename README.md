<br/>
<div align="center">
<a href="https://github.com/WirelessAlien/MovieDb">
<img src="https://github.com/WirelessAlien/MovieDB/blob/master/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="Logo" width="80" height="80">
</a>
<h3 align="center">ShowCase</h3>
<p align="center">
ShowCase (formerly Movie DB) is a fully open-source android application for exploring and organizing your personal collection of films and television series.

<br/>
<br/>
<a href="https://github.com/WirelessAlien/MovieDB/releases">Release .</a>  
<a href="https://github.com/WirelessAlien/MovieDB/issues">Report Bug .</a>
<a href="https://github.com/WirelessAlien/MovieDB/issues">Request Feature</a>
</p>
</div>

## About The Project

It offers synchronization with your TMDB account. Once logged in, you can sync your favorites, watchlist, rated movies, and more. It also integrates with a local database to keep track of your shows. You can assign various statuses to each title—such as ‘planned’, ‘watched’, or ‘dropped’—rate them, and note the dates you commenced and concluded each show.

It allows you to filter shows based on genre, release dates, and more. Detailed information on each show, including the cast, crew, and recommendations for similar titles, is also available. 

## Feature

- [x] Synchronization with TMDB
- [x] Add favorite, watchlist, rate with TMDB
- [x] Create List (Public/private)
- [x] Offline support (Local database to keep track of your shows)
- [x] Import/Export database
- [x] Support for Material You 
- [x] Get notified of movie and tv show release
- [x] And many more...

See the [open issues](https://github.com/WirelessAlien/MovieDB/issues) for a full list of proposed features (and known issues).

## Download

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/com.wirelessalien.android.moviedb/)

Or download the latest APK from the [Releases Section](https://github.com/WirelessAlien/MovieDB/releases/latest).

## Screenshots

<pre>
<img src="https://github.com/WirelessAlien/MovieDB/assets/121420261/b876ea1d-02a4-420e-a0c6-7b7085fcea83" width="130" height="280" />  <img src="https://github.com/WirelessAlien/MovieDB/assets/121420261/4e970fe9-d600-4d77-8837-97ef989edc92" width="130" height="280" />  <img src="https://github.com/WirelessAlien/MovieDB/assets/121420261/4e7765bc-eb24-4d4c-894f-40220a1c689e" width="130" height="280" />  <img src="https://github.com/WirelessAlien/MovieDB/assets/121420261/11d3282f-5f00-479f-9a49-7e07dd743d44" width="130" height="280" />  <img src="https://github.com/WirelessAlien/MovieDB/assets/121420261/aec79075-14b3-4fc9-82b9-a5888e0275fc" width="130" height="280" />  <img src="https://github.com/WirelessAlien/MovieDB/assets/121420261/dd4685bf-9b67-46b4-9bf8-b415289d153a" width="130" height="280" />
</pre>

### Build instructions

This application can be compiled in android studio.
You can compile it in command line with gradle also.
To use your own api key-
1. Get a free API Key at [https://www.themoviedb.org/settings/api](https://www.themoviedb.org/settings/api)
2. Enter your API in `config.properties`

 ```
api_read_access_token = "ENTER YOUR KEY";
 ```
```
api_key = "ENTER YOUR KEY";
 ```
4. Build

## Contributing

Any contributions you make are **greatly appreciated**.

If you have a suggestion that would make this better, please fork the repo and create a pull request. You can also simply open an issue with the tag "enhancement".
Don't forget to give the project a star! Thanks again!

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## Translate

<a href="https://hosted.weblate.org/engage/showcase/">
<img src="https://hosted.weblate.org/widget/showcase/strings/287x66-white.png" alt="Translation status" />
</a>

## License

Distributed under the GPL-3 License. See [GPL-3 License](https://www.gnu.org/licenses/gpl-3.0.txt) for more information.

## Donate 

<noscript><a href="https://liberapay.com/WirelessAlien/donate"><img alt="Donate using Liberapay" src="https://liberapay.com/assets/widgets/donate.svg"></a></noscript>  

<a href="https://www.buymeacoffee.com/wirelessalien" target="_blank"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-blue.png" alt="Buy Me A Coffee" style="height: 60px !important;width: 217px !important;" ></a>

## Acknowledgments

- The base source code is from [nvb / MovieDB](https://notabug.org/nvb/MovieDB) (GPL-v3)
