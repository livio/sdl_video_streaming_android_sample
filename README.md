# sdl_video_streaming_android_sample
## Getting Started
Download and install the latest release on an Android device. If you plug the device into a module running core with USB/AOA support, the app should begin streaming a video to the screen.

## Directions for Building
1. Clone 
2. Edit project settings.gradle to include the following (add in your own {path_to_sdl_android_lib})
```
include ':app', ':sdl_android'
project (":sdl_android").projectDir = new File("{path_to_sdl_android_lib}")
```
Then, run it. You can try replacing the video we use 'sdl.mp4' with a video of your own with the same name.

## Notes
- This app is for testing porposes only. 
- Touch events are not used in this app.
- The video stream used is not of the app itself, but a stored, local video file.

For questions contact 
#### Austin Kirk - [Github](https://github.com/askirk)
