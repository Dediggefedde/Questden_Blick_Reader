# About

Reader app for questden.org<br/>
This app aims for making reading quests and threads on questden.org enjoyable for smartphone-users.
[Download the apk](https://github.com/Dediggefedde/Questden_Blick_Reader/raw/release/app/release/app-release.apk)<br/>

# Features

* Reading:
  + Font size bigger/smaller
  + 3 NSFW modes: always hide (*SFW*, doesn't download spoiler-images), download/show on tap (*SFW?*) and always show spoilers (*NSFW*)
  + Hide all posts without images
  + three image sizes
  + Navigation to next/previous image/post
  + Tapping ref-links jumps to their post.
  + Links to open posts/threads/images in your browser on demand
  + fullview images with zoom
  + saves last-read position for each thread
  + Reply form with basic editor
* Chapter Overview
  + Connects with questden-wiki and shows available thread chapters.
* Watchlist
  + watched threads to get counters of new posts/images since your last visit
  + refresh watchlist to check all watched threads for new posts
* Download
  + Download threads and their images to read them while offline!
  + Image quality can be lowered to reduce traffic
* Synchronize with questden_BLICK
  + Synchronize devices or create backups
  + Works with the Questden_Blick userscript! Synchronize your reading status with your desktop-PC!
  + No communication with the server except you press "login", "upload" or "download"
* Offline backup
  + Can create and load backup files on your phone
  
# Requirements

Full support Android version >20.<br/>
Compatibility support Android version >17.

# Install

[Download the apk](https://github.com/Dediggefedde/Questden_Blick_Reader/raw/release/app/release/app-release.apk)<br/>
You might need to allow installing apps from unknown sources.
Open the file via browser or file-explorer and you will be prompted to install
Since I'm unknown on playstore, "play protect" will warn you once. 
If there is an update, the app will prompt you to download the latest apk from this website (same link as above).

# Quickstart

+ **Navigation**
  * Use the side-navigation to go to a board or your watchlist
  * Tap on a thread-title to go to the quest
  * Tap on thread-IDs to open that Post in your browser
  * Tap a message's text to toggle between navigation and fullscreen mode
  * Use the bottom navigation arrows to browse between images or board-pages
+ **Features**
  * Tap the sunglass for customisation (only-images-mode, font-size, SFW-mode)
  * Tap the "watch" button to add/remove a thread to your watchlist
  * The board-overview has shortened thread-texts. Tap to extent them.
  * Tap on images to see them fullscreen
    - The img-url will appear at the bottom. Tap to open in your browser
  * Tap the refresh button to update the current page
    - Watchlist: press the refresh-button to check for updates

---

# Screenshots

<p align="center">
<img src="./images/screenshots/board.png?raw=true" align="center"  width="20%"  />
<img src="./images/screenshots/quest+tools.png?raw=true" align="center"  width="20%"  />
<img src="./images/screenshots/quest_fullimgs.png?raw=true" align="center"  width="20%"  />
<img src="./images/screenshots/watchlist.png?raw=true" align="center"  width="20%"  />
<img src="./images/screenshots/img_fullview_landscape.png?raw=true" align="center"  width="60%"  />
</p>

# Onboarding Screens

<p align="center">
<img src="./images/onboarding/onboarding_00_welcome.png?raw=true" align="center"  width="20%"  />
<img src="./images/onboarding/onboarding_01_navigation.png?raw=true" align="center"  width="20%"  />
<img src="./images/onboarding/onboarding_02_boards.png?raw=true" align="center"  width="20%"  />
<img src="./images/onboarding/onboarding_03_thread.png?raw=true" align="center"  width="20%" />
<img src="./images/onboarding/onboarding_04_toolbar.png?raw=true" align="center"  width="20%"  />
<img src="./images/onboarding/onboarding_05_imagemode.png?raw=true" align="center" width="20%" />
<img src="./images/onboarding/onboarding_05_imagemode.png?raw=true" align="center" width="20%" />
<img src="./images/onboarding/onboarding_06_reply.png?raw=true" align="center" width="20%" />
<img src="./images/onboarding/onboarding_07_watchlist_downloaded.png?raw=true" align="center" width="20%" />
<img src="./images/onboarding/onboarding_08_sync.png?raw=true" align="center" width="20%" />
</p>

<p>The images in the screenshots are mostly dran by Toxoglossa in the quest "Moot Point" (NSFW)</p>

---

# Security and Privacy

The app does not collect or store any data about your behavior. It only requests questden.org when the user requests a page. 
It only stores app-specific settings, like last-read positions, your watch-list.<br>
To check for updates, the start will make a request to github once per start for a file <1kb.

Syncing with questden_BLICK requires a user-account at https://phi.pf-control.de/tgchan/interface.php?login.<br>
Requesting a sync will send and receive only necessary data from only that server. EU Privacy Policy is explained on the website.

The script uses bare volley https-requests qith only minimal data sent to questden.org.
The response is parsed using a custom made html parser based on jsoup without executing flash or javascript.

# License
    Copyright [2020] [Julian Bergmann]

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
