var exec = require('cordova/exec')
function MegacuboPlayer() {
    var self = this;
    self.appMetrics = {top:0, bottom: 0, right: 0, left: 0};
    self.on = function (type, cb){
        if(typeof(self.events[type]) == 'undefined'){
            self.events[type] = []
        }
        if(self.events[type].indexOf(cb) == -1){
            self.events[type].push(cb)
        }
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
    self.play = function(uri, mimetype, cookie, success, error) {
        self.currentTime = 0;
        self.duration = 0;
        exec(success, error, "tv.megacubo.player", "play", [uri, mimetype, cookie])
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
    self.uiVisible = function(visible, success, error) {
        exec(success, error, "tv.megacubo.player", "ui", [visible])
    }
    self.seek = function(to, success, error) {
        clearTimeout(self.seekTimer)
        exec(success, error, "tv.megacubo.player", "seek", [to])
        self.timeUpdateLocked = true
        self.seekTime = setTimeout(() => {
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
    self.onTrackingEvent = e => {
        if(e.data && ['{', '"'].indexOf(e.data.charAt(0)) != -1){
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
        self.on('time', e => {
            e.currentTime = e.currentTime / 1000;
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
        exec(() => {}, console.error, "tv.megacubo.player", "getAppMetrics", [])
    }
    self.init()
}

module.exports = new MegacuboPlayer()
