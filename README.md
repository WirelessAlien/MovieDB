<br/>
<div align="center">
<a href="https://github.com/ShaanCoding/ReadME-Generator">
<img src="https://github.com/WirelessAlien/MovieDB/blob/master/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="Logo" width="80" height="80">
</a>
<h3 align="center">Movie DB</h3>
<p align="center">
A Free and Open-source Offline (also has Online Support) Movie Database Android Application

<br/>
<br/>
<a href="https://github.com/WirelessAlien/MovieDB/releases">Release .</a>  
<a href="https://github.com/WirelessAlien/MovieDB/issues">Report Bug .</a>
<a href="https://github.com/WirelessAlien/MovieDB/issues">Request Feature</a>
</p>
</div>

## About The Project

Movie DB is an offline (also has online support) movie database android application. The required data are requested from themoviedb.org. The database (called 'saved' in the application) is offline.
To use online mode you need to have account in TMDB. After login, the favourite, watchlist, rate, list feature can be use to sync with https://www.themoviedb.org

## Feature

- [x] Login to TMDB
- [x] Add favorite, watchlist, rate with TMDB
- [x] Offline support (If you don't want to use TMDB login)
- [x] Import/Export database

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

## License

Distributed under the GPL-3 License. See [GPL-3 License](https://www.gnu.org/licenses/gpl-3.0.txt) for more information.

## Acknowledgments

- The base source code is from [nvb / MovieDB](https://notabug.org/nvb/MovieDB) (GPL-v3)
- App base icon by icelloid from <a href="https://thenounproject.com/browse/icons/term/video-rating/" target="_blank" title="Video Rating Icons">Noun Project</a> (CC BY 3.0)
