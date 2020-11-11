## About

Reader app for questden.org

This app aims for making reading quests and threads on questden.org enjoyable for smartphone-users.

## Screenshots

<p align="center">
<img src="./screenshots/Screenshot_20200901-014309.png?raw=true" align="center"  width="15%"  />
<img src="./screenshots/Screenshot_20201103-005305.png?raw=true" align="center"  width="15%"  />
<img src="./screenshots/Screenshot_20201103-005321.png?raw=true" align="center"  width="15%"  />
<img src="./screenshots/Screenshot_20201103-005345.png?raw=true" align="center"  width="15%" />
<img src="./screenshots/Screenshot_20201103-005926.png?raw=true" align="center"  width="15%"  />
<img src="./screenshots/Screenshot_20201103-005939.png?raw=true" align="center" width="15%" />
</p>

## Readability

Much like my userscript questden_BLICK, there is emphasis on readability and thread-management.

Based on scientific publications I adjusted default colors, text-sizes, margins etc. However, there are also customization options to adapt the website to your needs.

## Features

* improved readability
* customizable display
  + Font size bigger/smaller
  + 3 NSFW modes: always hide (*SFW*, doesn't download spoiler-images), download/show on tap (*NSFW?*) and always show spoilers (*NSFW*)
  + Hide all posts without images
* saves last-read position for each post
* Position/Navigation
  + Buttons for first/last/next/previous image
  + Position indicator for images in thread
  + tap links to jump and highlight to their posts
* Watchlist
  + separate list of all your watched threads/quests
  + new posts and images since your last visit
  + update only posts on this list per button.
* Synchronize with questden_BLICK
  + Experimental: only downloading works for now

---

## Supports

Full support Android version >20.<br/>
Compatibility support Android version >17.

## How to Install

**Currently in beta-phase (WIP branch)**</br>
[Download the apk](https://github.com/Dediggefedde/Questden_Blick_Reader/raw/release/app/release/app-release.apk)<br/>
You might need to allow installing apps outside the playstore.
Since I'm unknown on playstore, "play protect" will warn you once. 

## How to use

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

# Security and Privacy

The app does not collect or store any data about your behavior. It only requests questden.org when the user requests a page. 
It only stores app-specific settings, like last-read positions, your watch-list.

Syncing with questden_BLICK requires a useraccount at https://phi.pf-control.de/tgchan/interface.php?login. 
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
