var exec = require('cordova/exec')

function MegacuboPlayer() {
    var self = this;

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
        exec(self.onTrackingEvent, function() {}, "cordova-megacubo-player-plugin", "bind", [navigator.userAgent])
        exec(success, error, "cordova-megacubo-player-plugin", "play", [uri, mimetype, cookie])
    }

    self.stop = function(success, error) {
        exec(success, error, "cordova-megacubo-player-plugin", "stop", [])
    }

    self.pause = function(success, error) {
        exec(success, error, "cordova-megacubo-player-plugin", "pause", [])
    }

    self.resume = function(success, error) {
        exec(success, error, "cordova-megacubo-player-plugin", "resume", [])
    }

    self.mute = function(success, error) {
        exec(success, error, "cordova-megacubo-player-plugin", "mute", [])
    }

    self.unMute = function(success, error) {
        exec(success, error, "cordova-megacubo-player-plugin", "unMute", [])
    }

    self.seek = function(to, success, error) {
        console.warn("RATIO", to)
        clearTimeout(self.seekTimer)
        self.currentTime = self.seeking = to
        exec(success, error, "cordova-megacubo-player-plugin", "seek", [to])
        self.seekTimer = setTimeout(() => {
            self.seeking = false            
        }, 2000)
        self.emit('timeupdate')
    }

    self.ratio = function(rt, success, error) {
        console.warn("RATIO", rt)
        exec(success, error, "cordova-megacubo-player-plugin", "ratio", [rt])
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
        self.seeking = false
        self.on('ratio', e => {
            self.aspectRatio = e.ratio
            self.videoWidth = e.width
            self.videoHeight = e.height
        })
        self.on('time', e => {
            if(!self.seeking){
                console.warn('EXVENT', e)
                if(e.duration < e.currentTime){
                    e.duration = e.currentTime
                }
                if(e.currentTime != self.currentTime){
                    self.currentTime = e.currentTime
                    self.emit('timeupdate')
                }
                if(e.duration != self.duration){
                    self.duration = e.duration
                    self.emit('durationchange')
                }
            }
        })
    }

    self.init()
}

module.exports = new MegacuboPlayer()