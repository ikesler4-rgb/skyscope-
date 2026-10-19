# SkyScope

Phone app that measures how well your phone sees positioning satellites
(GPS, Galileo, GLONASS, BeiDou) where you stand, and how to improve it.

Project doc: https://claude.ai/code/artifact/d07ec93b-7ea5-47bb-b6e0-dadc49622ced

## android/
Native Android app with real per-satellite data.
Open the folder in Android Studio (File > Open), wait for sync, connect an
Android phone with USB debugging on, press Run. Details in android/README.md.

## iphone/
index.html: works in Safari on iPhone. Measures reported accuracy and how
much the position wanders, plus a spot finder.
To use it: put index.html in a folder, drag the folder onto
app.netlify.com/drop (free), open the link on the iPhone in Safari,
then Share > Add to Home Screen.
