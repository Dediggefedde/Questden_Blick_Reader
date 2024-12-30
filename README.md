# About

Reader app for questden.org

This app aims for making reading quests and threads on questden.org enjoyable for smartphone-users!
[Download Apk!](https://github.com/Dediggefedde/Questden_Blick_Reader/releases/latest/download/Questden_Blick_Reader.apk)

---

# Summary of Features
* **Reading**: Cleaner posts, bigger font, bigger images, better touch support
* **Navigating**: Tracking reading position, chapter overview, prev/next for next image post, hide non-image posts
* **Watching**: Keep track of new posts
* **Offline**: Download thred with images for offline reading
* **Synchronize**: sync watchlist/reading position with your browser
* **Privacy**: no background activity, no internet communcation unless triggert by user, no adds, no hidden data collection

# Features (Detail)

* Reading:
  + Make font size bigger/smaller
  + 3 NSFW modes: always hide (*SFW*, doesn't download spoiler-images), download/show on tap (*SFW?*) and always show spoilers (*NSFW*)
  + Image-mode: hide all posts without images
  + three image layouts (small ~ 1/4 screen width, big ~ 1/2 screen width, full ~full screen width)
  + In full images layout, thumbnails can be replaced with full resolution images
  + Images can be opened by tapping and zoomed. Tapping a link opens their url in a browser.
  + Navigation to next/previous image/post depending on navigation mode.
  + Tapping ref-links jumps to their post. Back-button jumps back to previous post.
  + Tap the post IDs to open the thread in your browser 
  + Tracking last-read position for each thread (Post at the top frame)
  + Reply form with basic editor
* Chapter Overview
  + shows available thread chapters
  + Connects with questden-wiki to read the infobox information
* Watchlist
  + watch threads to get counters of new posts/images since your last visit
  + "refresh" watchlist to check all watched threads for new posts
* Download
  + Download threads and their images to read them while offline!
  + Image quality can be lowered to reduce traffic
* Synchronize with questden_BLICK
  + Synchronize devices via server or create backups
  + Works with the Questden_Blick userscript! Synchronize your reading status and watchlist with your desktop-PC!
  + No communication with the server except you press "login", "upload" or "download"
  + It uses my own server for communcation (located in Germany, following EU Data Privacy regulations)
* Offline backup
  + Can create and load backup files on your phone

---

# Requirements

Full support Android 4.4 (API 20)<br/>
Compatibility support Android version 4.1 (API 17).
Tested using Moto Z Play with Lineage OS 18.1, Nothing Phone 2a with Android 14, and Emulated "Medium Phone" (Android 11). 

# Install

[Download the apk](https://github.com/Dediggefedde/Questden_Blick_Reader/releases/latest/download/Questden_Blick_Reader.apk)<br/>
You might need to allow installing apps from unknown sources:
* Open the file via browser or file-explorer and you will be prompted to install
* You might need to allow installing APKs from your browser app.
* Since I'm not on playstore, "play protect" might warn you once or ask you to scan the app.

Afterwards, the app should install without issues and take around 20 MB of space on your phone.<br>
On first start, the app will show you the "onboarding" images from below. You can zoom into them by a pinching gesture on your phone.<br>
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
