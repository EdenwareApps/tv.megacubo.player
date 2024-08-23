var exec = require('cordova/exec')
function MegacuboPlayer() {
    var self = this
    self.appMetrics = {top: 0, bottom: 0, right: 0, left: 0};
    self.on = function (type, cb){
        if(typeof(self.events[type]) == 'undefined'){
            self.events[type] = []
        }
        if(self.events[type].indexOf(cb) == -1){
            self.events[type].push(cb)
        }
    }
    self.once = function (type, cb){
        const listener = () => {
            cb()
            self.off(type, listener)
        }
        self.on(type, listener)
    }
    self.off = function (type, cb){
        if(typeof(self.events[type]) != 'undefined'){
            if(typeof(cb) == 'function'){
                let i = self.events[type].indexOf(cb)
                if(i != -1){
                    self.events[type].splice(i, 1)
                }
            } else {
                delete self.events[type]
            }
        }
    }
    self.emit = function (){
        var a = Array.from(arguments)
        var type = a.shift()
        if(typeof(self.events[type]) != 'undefined'){
            self.events[type].forEach(function (f){
                f.apply(null, a)
            })
        }
    }
    self.play = function(uri, mimetype, subtitles, cookie, mediatype, success, error) {
        self.currentTime = 0;
        self.duration = 0;
        self._audioTracks = null;
        self._subtitleTracks = null;
        exec(success, error, "tv.megacubo.player", "play", [uri, mimetype, subtitles, cookie, mediatype])
    }
    self.volume = function(level, success, error) {
        exec(success, error, "tv.megacubo.player", "volume", [level])
    }
    self.stop = function(success, error) {
        exec(success, error, "tv.megacubo.player", "stop", [])
    }
    self.pause = function(success, error) {
        exec(success, error, "tv.megacubo.player", "pause", [])
    }
    self.resume = function(success, error) {
        exec(success, error, "tv.megacubo.player", "resume", [])
    }
    self.mute = function(success, error) {
        exec(success, error, "tv.megacubo.player", "mute", [])
    }
    self.unmute = function(success, error) {
        exec(success, error, "tv.megacubo.player", "unMute", [])
    }
    self.restartApp = function(success, error) {
        exec(success, error, "tv.megacubo.player", "restart", [])
    }
    self.getAppMetrics = function(success, error) {
        exec(success, error, "tv.megacubo.player", "getAppMetrics", [])
    }
    self.getNetworkIp = function(success, error) {
        exec(success, error, "tv.megacubo.player", "getNetworkIp", [])
    }
    self.uiVisible = function(visible, success, error) {
        exec(success, error, "tv.megacubo.player", "ui", [visible])
    }
    self.enterFullScreen = function(success, error) {
        exec(success, error, "tv.megacubo.player", "enterFullScreen", [])
    }
    self.leaveFullScreen = function(success, error) {
        exec(success, error, "tv.megacubo.player", "leaveFullScreen", [])
    }
    self.seek = function(to, success, error) {
        clearTimeout(self.seekTimer)
        const by = to - self.currentTime
        exec(success, error, "tv.megacubo.player", "seekBy", [by])
        self.timeUpdateLocked = true
        self.seekTimer = setTimeout(() => {
            self.timeUpdateLocked = false
        }, 2000)
    }
    self.ratio = function(r, success, error) {
        if(typeof(r) == 'number' && !isNaN(r)){
			if(r != self.aspectRatio){
				exec(success, error, "tv.megacubo.player", "ratio", [r])
			}
		} else {
			console.error('BAD RATIO VALUE '+ typeof(r), r)
		}
    }
    self.setPlaybackRate = function(rate, success, error) {
        if(typeof(rate) == 'number' && !isNaN(rate)){
			if(rate != self.playbackRate){
				exec(success, error, "tv.megacubo.player", "rate", [rate])
			}
		} else {
			console.error('BAD PLAYBACK RATE VALUE '+ typeof(rate), rate)
		}
    }
    self.audioTrack = function(trackId, success, error) {
        if(typeof(trackId) != 'undefined' && Array.isArray(self._audioTracks) && self._audioTracks.length > 1){
			console.error('AUDIO TRACK '+ typeof(trackId), trackId)
			exec(success, error, "tv.megacubo.player", "audioTrack", [trackId])
		} else {
			console.error('BAD AUDIO TRACK VALUE '+ typeof(trackId), trackId)
		}
    }
    self.subtitleTrack = function(trackId, success, error) {
        console.error('SUBTITLE TRACK CHANGE '+JSON.stringify({trackId, tracks: self._subtitleTracks}))
        if(typeof(trackId) != 'undefined' && Array.isArray(self._subtitleTracks) && self._subtitleTracks.length >= 1){
			console.error('SUBTITLE TRACK '+ typeof(trackId), trackId)
			exec(success, error, "tv.megacubo.player", "subtitleTrack", [trackId])
		} else {
			console.error('BAD SUBTITLE TRACK VALUE '+ typeof(trackId), trackId)
		}
    }
    self.audioTracks = function() {
        return Array.isArray(self._audioTracks) ? self._audioTracks : [{id: 0, name: 'Default'}]
    }
    self.subtitleTracks = function() {
        return Array.isArray(self._subtitleTracks) ? self._subtitleTracks : []
    }
    self.onTrackingEvent = e => {
        if(e.data && ['{', '[', '"'].indexOf(e.data.charAt(0)) != -1){
            e.data = JSON.parse(e.data)
        }
        self.emit(e.type, e.data)
    }
    self.init = () => {
        self.seekTimer = 0
        self.events = {}
        self.on('ratio', e => {
            self.aspectRatio = e.ratio
            self.videoWidth = e.width
            self.videoHeight = e.height
        })
        self.on('appMetrics', e => {
            self.appMetrics = e
            self.emit('appmetrics', e)
        })
        self.on('tracks', e => {
            console.error('TRACKS CHANGED '+JSON.stringify(e))
            self._audioTracks = e.filter(e => e.type.indexOf('audio') != -1)
            self._subtitleTracks = e.filter(e => e.type.indexOf('text') != -1)
            self.emit('audioTracks', self._audioTracks)
            self.emit('subtitleTracks', self._subtitleTracks)
        })
        self.on('networkIp', e => {
            self.networkIp = e
            self.emit('network-ip', e)
        })
        self.on('nightMode', info => {
            if(!self.nightModeInfo){
                self.nightModeInfo = info
                self.emit('nightmode', info)
            }
        })
        self.on('time', e => {
            e.currentTime = Math.max(e.currentTime / 1000, 0);
            e.duration = e.duration / 1000;
            if(e.duration < e.currentTime){
                e.duration = e.currentTime + 1;
            }
            if(e.currentTime > 0 && e.currentTime != self.currentTime){
                self.currentTime = e.currentTime
                if(!self.timeUpdateLocked){
                    self.emit('timeupdate')
                }
            }
            if(e.duration && e.duration != self.duration){
                self.duration = e.duration
                self.emit('durationchange')
            }
        })
        exec(self.onTrackingEvent, function() {}, "tv.megacubo.player", "bind", [navigator.userAgent])
        self.getAppMetrics(() => {}, console.error)
    }
    self.init()
}

module.exports = new MegacuboPlayer()
