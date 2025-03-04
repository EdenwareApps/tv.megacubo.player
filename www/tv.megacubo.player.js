const exec = require('cordova/exec');

class MegacuboPlayer {
    constructor() {
        this.events = {};
        this.seekTimer = null;
        this.timeUpdateLocked = false;
        this.init();
    }

    on(type, cb) {
        if (!this.events[type]) {
            this.events[type] = [];
        }
        if (!this.events[type].includes(cb)) {
            this.events[type].push(cb);
        }
    }

    once(type, cb) {
        const listener = (...args) => {
            cb(...args);
            this.off(type, listener);
        };
        this.on(type, listener);
    }

    off(type, cb) {
        if (this.events[type]) {
            if (typeof cb === 'function') {
                const index = this.events[type].indexOf(cb);
                if (index !== -1) {
                    this.events[type].splice(index, 1);
                }
            } else {
                delete this.events[type];
            }
        }
    }

    emit(type, ...args) {
        if (this.events[type]) {
            this.events[type].forEach(cb => cb(...args));
        }
    }

    play(uri, mimetype, subtitles, cookie, mediatype, success, error) {
        this.currentTime = 0;
        this.duration = 0;
        this._audioTracks = null;
        this._subtitleTracks = null;
        exec(success, error, "tv.megacubo.player", "play", [uri, mimetype, subtitles, cookie, mediatype]);
    }

    volume(level, success, error) {
        exec(success, error, "tv.megacubo.player", "volume", [level]);
    }

    stop(success, error) {
        exec(success, error, "tv.megacubo.player", "stop", []);
    }

    pause(success, error) {
        exec(success, error, "tv.megacubo.player", "pause", []);
    }

    resume(success, error) {
        exec(success, error, "tv.megacubo.player", "resume", []);
    }

    mute(success, error) {
        exec(success, error, "tv.megacubo.player", "mute", []);
    }

    unmute(success, error) {
        exec(success, error, "tv.megacubo.player", "unMute", []);
    }

    restartApp(success, error) {
        exec(success, error, "tv.megacubo.player", "restart", []);
    }

    getNetworkIp(success, error) {
        exec(success, error, "tv.megacubo.player", "getNetworkIp", []);
    }

    uiVisible(visible, success, error) {
        exec(success, error, "tv.megacubo.player", "ui", [visible]);
    }

    seek(to, success, error) {
        clearTimeout(this.seekTimer);
        const by = to - this.currentTime;
        exec(success, error, "tv.megacubo.player", "seekBy", [by]);
        this.timeUpdateLocked = true;
        this.seekTimer = setTimeout(() => {
            this.timeUpdateLocked = false;
        }, 2000);
    }

    ratio(r, success, error) {
        if (typeof r === 'number' && !isNaN(r)) {
            if (r !== this.aspectRatio) {
                exec(success, error, "tv.megacubo.player", "ratio", [r]);
            }
        } else {
            console.error('BAD RATIO VALUE', r);
        }
    }

    setPlaybackRate(rate, success, error) {
        if (typeof rate === 'number' && !isNaN(rate)) {
            if (rate !== this.playbackRate) {
                exec(success, error, "tv.megacubo.player", "rate", [rate]);
            }
        } else {
            console.error('BAD PLAYBACK RATE VALUE', rate);
        }
    }

    audioTrack(trackId, success, error) {
        if (trackId !== undefined && Array.isArray(this._audioTracks) && this._audioTracks.length > 1) {
            exec(success, error, "tv.megacubo.player", "audioTrack", [trackId]);
        } else {
            console.error('BAD AUDIO TRACK VALUE', trackId);
        }
    }

    subtitleTrack(trackId, success, error) {
        if (trackId !== undefined && Array.isArray(this._subtitleTracks) && this._subtitleTracks.length >= 1) {
            exec(success, error, "tv.megacubo.player", "subtitleTrack", [trackId]);
        } else {
            console.error('BAD SUBTITLE TRACK VALUE', trackId);
        }
    }

    audioTracks() {
        return Array.isArray(this._audioTracks) ? this._audioTracks : [{ id: 0, name: 'Default' }];
    }

    subtitleTracks() {
        return Array.isArray(this._subtitleTracks) ? this._subtitleTracks : [];
    }

    onTrackingEvent(e) {
        if (e.data && ['{', '[', '"'].includes(e.data.charAt(0))) {
            e.data = JSON.parse(e.data);
        }
        this.emit(e.type, e.data);
    };

    init() {
        this.on('ratio', e => {
            this.aspectRatio = e.ratio;
            this.videoWidth = e.width;
            this.videoHeight = e.height;
        });

        this.on('tracks', e => {
            this._audioTracks = e.filter(track => track.type.includes('audio'));
            this._subtitleTracks = e.filter(track => track.type.includes('text'));
            this.emit('audioTracks', this._audioTracks);
            this.emit('subtitleTracks', this._subtitleTracks);
        });

        this.on('networkIp', e => {
            this.networkIp = e;
            this.emit('network-ip', e);
        });

        this.on('nightMode', info => {
            if (!this.nightModeInfo) {
                this.nightModeInfo = info;
                this.emit('nightmode', info);
            }
        });

        this.on('time', e => {
            e.currentTime = Math.max(e.currentTime / 1000, 0);
            e.duration = e.duration / 1000;
            if (e.duration < e.currentTime) {
                e.duration = e.currentTime + 1;
            }
            if (e.currentTime > 0 && e.currentTime !== this.currentTime) {
                this.currentTime = e.currentTime;
                if (!this.timeUpdateLocked) {
                    this.emit('timeupdate');
                }
            }
            if (e.duration && e.duration !== this.duration) {
                this.duration = e.duration;
                this.emit('durationchange');
            }
        });

        exec(this.onTrackingEvent.bind(this), () => {}, "tv.megacubo.player", "bind", [navigator.userAgent]);
    }
}

module.exports = new MegacuboPlayer();