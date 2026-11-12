/* TB for Samsung Tizen TVs */
(function () {
  'use strict';

  var PLAYLIST_URL = 'https://raw.githubusercontent.com/ZeeyKhm/TB/main/playlist/tb.m3u8';
  var HIDDEN_URLS = [
    'https://raw.githubusercontent.com/BuddyChewChew/app-m3u-generator/refs/heads/main/playlists/plutotv_all.m3u',
    'https://www.apsattv.com/whaletvplus_all.m3u',
    'https://www.apsattv.com/xiaomi.m3u',
    'https://www.apsattv.com/vidaa.m3u'
  ];
  var HIDDEN_PIN = '1234';
  var HIDDEN_CACHE_KEY = 'tb.hidden.v1';

  var player = document.getElementById('player');
  var frameEl = document.getElementById('playerFrame');
  var splash = document.getElementById('splash');
  var splashText = document.getElementById('splashText');
  var panel = document.getElementById('panel');
  var listEl = document.getElementById('list');
  var countEl = document.getElementById('count');
  var sourceEl = document.getElementById('source');
  var infoBtn = document.getElementById('infoBtn');
  var toastEl = document.getElementById('toast');
  var pinEl = document.getElementById('pin');
  var pinDots = document.getElementById('pinDots');
  var pinError = document.getElementById('pinError');
  var pinTitle = document.getElementById('pinTitle');

  var mainChannels = [];
  var hiddenChannels = [];
  var hiddenMode = false;
  var focusTv = 0;
  var current = null;
  var panelOpen = true;
  var infoFocused = false;
  var bootFailed = false;
  var hls = null;
  var triedHls = false;
  var avplayTried = false;
  var playSeq = 0;
  var frameMode = false;
  var videoWatch = null;
  var toastTimer = null;

  var infoClicks = 0;
  var infoLast = 0;
  var pinOpen = false;
  var pinValue = '';
  var pinHiding = false;

  /* Tizen TVs play HLS natively via the built-in player. hls.js is only a
     fallback for browsers that lack native HLS support (dev/QA). */
  var IS_TIZEN = typeof window.tizen !== 'undefined';
  var NATIVE_HLS = !!(player.canPlayType && player.canPlayType('application/vnd.apple.mpegurl'));

  /* ------------------------------------------------------------ helpers */
  function toast(message, isError) {
    toastEl.textContent = message;
    toastEl.className = 'toast show' + (isError ? ' error' : '');
    if (toastTimer) clearTimeout(toastTimer);
    toastTimer = setTimeout(function () {
      toastEl.className = 'toast' + (isError ? ' error' : '');
    }, isError ? 5000 : 2600);
  }

  function channels() { return hiddenMode ? hiddenChannels : mainChannels; }

  /* One list only: TV and radio channels together, in playlist order. */
  function filtered() { return channels(); }

  function focusIndex() { return focusTv; }
  function setFocusIndex(i) { focusTv = i; }

  function counts() {
    var tv = 0, radio = 0;
    channels().forEach(function (c) { if (c.radio) radio++; else tv++; });
    return { tv: tv, radio: radio };
  }

  /* ------------------------------------------------------------ parsing */
  function parseM3U(text) {
    var out = [];
    var cur = null;
    var section = '';
    var referrer = '';
    var frameUrl = '';

    text.split(/\r?\n/).forEach(function (raw) {
      var line = raw.trim();
      if (!line) return;

      if (line.charAt(0) === '#') {
        var ref = /^#EXTVLCOPT:http-referrer=(.+)$/i.exec(line);
        if (ref) { referrer = ref[1].trim(); return; }

        var frame = /^#TB-IFRAME:\s*(.+)$/i.exec(line);
        if (frame) { frameUrl = frame[1].trim(); return; }

        var marker = /^#{4,}\s*(.+?)\s*#{4,}$/.exec(line);
        if (marker) { section = marker[1].toUpperCase(); return; }

        if (/^#EXTINF/i.test(line)) {
          var comma = line.indexOf(',');
          var head = comma >= 0 ? line.slice(0, comma) : line;
          var name = comma >= 0 ? line.slice(comma + 1).trim() : '';
          var group = /group-title="([^"]*)"/i.exec(head);
          cur = {
            name: name || (group ? group[1] : 'Channel'),
            url: null,
            group: group ? group[1] : '',
            section: section || 'CHANNELS',
            radio: false,
            referrer: referrer,
            frame: frameUrl
          };
          referrer = '';
          frameUrl = '';
          out.push(cur);
        }
        return;
      }

      if (cur && !cur.url) {
        var bar = line.indexOf('|');
        cur.url = bar >= 0 ? line.slice(0, bar) : line;
        if (referrer && !cur.referrer) cur.referrer = referrer;
        if (frameUrl) cur.frame = frameUrl;
        referrer = '';
        frameUrl = '';
        if (bar >= 0) {
          line.slice(bar + 1).split('|').forEach(function (part) {
            var eq = part.indexOf('=');
            if (eq <= 0) return;
            var key = part.slice(0, eq).toLowerCase();
            var value = part.slice(eq + 1);
            if (key === 'referer' || key === 'referrer') cur.referrer = value;
            if (key === 'user-agent') cur.userAgent = value;
          });
        }
      }
    });

    return out.filter(function (c) { return !!c.url; }).map(function (c) {
      c.radio = /radio/i.test(c.name + ' ' + c.group);
      return c;
    });
  }

  function fetchText(url) {
    return fetch(url, { cache: 'no-store' }).then(function (response) {
      if (!response.ok) throw new Error('HTTP ' + response.status);
      return response.text();
    });
  }

  /* Some playlist URLs are short API links that redirect (307) to the real
     manifest. Resolve them first: neither AVPlay nor <video> reliably follows
     those redirects. Falls back to the original URL on any failure. */
  function resolveMediaUrl(url, done) {
    if (/\.(m3u8|mpd|mp4|m4v|ts|aac|mp3)(\?|$)/i.test(url)) { done(url); return; }
    var settled = false;
    var timer = setTimeout(function () {
      if (settled) return;
      settled = true;
      done(url);
    }, 4000);
    fetch(url, { cache: 'no-store' }).then(function (response) {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      done(response && response.url ? response.url : url);
    }).catch(function () {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      done(url);
    });
  }

  /* ------------------------------------------------------------ rendering */
  function render() {
    var counts_ = counts();
    countEl.textContent = counts_.tv + ' TV · ' + counts_.radio + ' radio';
    sourceEl.textContent = hiddenMode ? 'hidden playlists' : 'main playlist';
    infoBtn.className = 'info-btn' + (infoFocused ? ' focused' : '');

    var items = filtered();
    var focus = focusIndex();
    if (focus >= items.length) { focus = items.length ? items.length - 1 : 0; setFocusIndex(focus); }

    var html = '';
    var lastSection = null;
    for (var i = 0; i < items.length; i++) {
      var c = items[i];
      if (c.section !== lastSection) {
        html += '<div class="section">' + escapeHtml(c.section) + '</div>';
        lastSection = c.section;
      }
      html += '<div class="row' + (i === focus && !infoFocused ? ' focused' : '') +
        (current && current.url === c.url ? ' playing' : '') +
        '" data-i="' + i + '"><span class="dot"></span><span class="name">' +
        escapeHtml(c.name) + '</span></div>';
    }
    if (!items.length) {
      html = '<div class="section">No channels in this section</div>';
    }
    listEl.innerHTML = html;
    scrollToFocus(false);
  }

  function escapeHtml(s) {
    return String(s).replace(/[&<>"]/g, function (ch) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[ch];
    });
  }

  function updateFocus() {
    infoBtn.className = 'info-btn' + (infoFocused ? ' focused' : '');
    var rows = listEl.querySelectorAll('.row');
    var focus = focusIndex();
    var items = filtered();
    for (var i = 0; i < rows.length; i++) {
      var row = rows[i];
      var idx = parseInt(row.getAttribute('data-i'), 10);
      var channel = items[idx];
      row.className = 'row' +
        (idx === focus && !infoFocused ? ' focused' : '') +
        (current && channel && current.url === channel.url ? ' playing' : '');
    }
    if (!infoFocused) scrollToFocus(true);
  }

  function scrollToFocus(smooth) {
    var row = listEl.querySelector('.row.focused');
    if (!row) return;
    var top = row.offsetTop - listEl.offsetTop;
    var bottom = top + row.offsetHeight;
    if (top < listEl.scrollTop || bottom > listEl.scrollTop + listEl.clientHeight) {
      listEl.scrollTop = top - listEl.clientHeight / 2 + row.offsetHeight / 2;
    }
  }

  function moveFocus(delta) {
    var items = filtered();
    if (!items.length) return;
    if (infoFocused) {
      if (delta > 0) { infoFocused = false; updateFocus(); }
      return;
    }
    var next = focusIndex() + delta;
    if (next < 0) { infoFocused = true; updateFocus(); return; }
    if (next > items.length - 1) next = items.length - 1;
    setFocusIndex(next);
    updateFocus();
  }

  /* ------------------------------------------------------------ playback */
  function loadScript(src, done) {
    var script = document.createElement('script');
    script.src = src;
    script.onload = function () { done(true); };
    script.onerror = function () { done(false); };
    document.body.appendChild(script);
  }

  function startHls(url) {
    loadScript('https://cdn.jsdelivr.net/npm/hls.js@1.5.13/dist/hls.min.js', function (ok) {
      if (!ok || !window.Hls || !window.Hls.isSupported()) {
        toast('This TV cannot play that stream', true);
        return;
      }
      if (hls) { hls.destroy(); }
      hls = new window.Hls({ lowLatencyMode: true, backBufferLength: 30 });
      hls.loadSource(url);
      hls.attachMedia(player);
      hls.on(window.Hls.Events.ERROR, function (_, data) {
        if (data && data.fatal) {
          hls.destroy();
          hls = null;
          toast('Could not play ' + (current ? current.name : 'stream') +
            ' (stream offline or blocked)', true);
        }
      });
      var p = player.play();
      if (p && p.catch) p.catch(function () {});
    });
  }

  /* ------------------------------------------------------------ AVPlay
     Samsung's native engine handles containers the HTML5 <video> element
     rejects: raw MPEG-TS and DASH (.mpd). It is only used after the
     <video> element fails, so normal HLS playback is unchanged. */
  var avplayActive = false;
  var avplayToken = 0;
  var diagOpen = false;

  function avplayApi() {
    try {
      if (window.webapis && window.webapis.avplay) return window.webapis.avplay;
    } catch (e) { /* ignore */ }
    return null;
  }

  function stopAvplay() {
    var api = avplayApi();
    avplayToken++;               /* invalidate callbacks from the old player */
    if (player.dataset) player.dataset.avpaused = '0';
    if (!avplayActive) return;
    avplayActive = false;
    if (!api) return;
    try { api.stop(); } catch (e) { /* ignore */ }
    try { api.close(); } catch (e) { /* ignore */ }
  }

  function playbackError(channel, err, api) {
    var state = '?';
    try { if (api && api.getState) state = String(api.getState()); } catch (e) { /* ignore */ }
    var reason = err ? ((err.name || 'error') + (err.message ? ': ' + err.message : '')) : 'no details';
    var lines = [
      'Could not play ' + channel.name + '.',
      '',
      'URL: ' + String(channel.url).split('|')[0],
      'Reason: ' + reason,
      'AVPlay state: ' + state
    ];
    if (channel.referrer) {
      lines.push('', 'This channel needs the broadcaster\'s referrer header, ' +
        'which the TV cannot send. It only plays in the broadcaster\'s own app.');
    }
    lines.push('', 'Press OK to continue.');
    splashText.textContent = lines.join('\n');
    splash.className = 'splash';
    diagOpen = true;
    toast('Could not play ' + channel.name, true);
  }

  function setAvplayVisible(visible) {
    var api = avplayApi();
    if (!api || !avplayActive) return;
    try {
      if (visible) api.setDisplayRect(0, 0, window.innerWidth, window.innerHeight);
      else api.setDisplayRect(0, 0, 1, 1);
    } catch (e) { /* ignore */ }
  }

  function startAvplay(channel) {
    var api = avplayApi();
    if (!api) return false;
    stopAvplay();
    var token = avplayToken;
    var seq = playSeq;
    var url = String(channel.url).split('|')[0];

    function alive() { return token === avplayToken; }

    try {
      player.pause();
      player.removeAttribute('src');
      player.load();
    } catch (e) { /* ignore */ }

    function fail(err) {
      if (!alive()) return;        /* stale event from a player we already closed */
      avplayToken++;
      avplayActive = false;
      try { api.stop(); } catch (e) { /* ignore */ }
      try { api.close(); } catch (e) { /* ignore */ }
      playbackError(channel, err, api);
    }

    try {
      api.open(url);
      api.setDisplayRect(0, 0, window.innerWidth, window.innerHeight);
      if (player.dataset) player.dataset.avpaused = '0';
      api.setListener({
        onerror: fail,
        onstreamcompleted: function () { if (alive()) stopAvplay(); },
        onbufferingstart: function () { },
        onbufferingcomplete: function () { }
      });
      avplayActive = true;
      api.prepareAsync(function () {
        if (!alive()) return;
        try {
          api.play();
          splash.className = 'splash hidden';
          toast('Playing ' + channel.name);
          setTimeout(function () { checkAvplayVideo(api, channel, seq, token); }, 5000);
        } catch (e) { fail(e); }
      }, fail);
      return true;
    } catch (e) {
      avplayActive = false;
      return false;
    }
  }

  /* AVPlay can end up sound-only as well. It knows which tracks it opened, so
     ask it: when there is no video track, hand the stream to hls.js (MSE),
     which demuxes it in JavaScript and is the last playback path available. */
  function checkAvplayVideo(api, channel, seq, token) {
    if (token !== avplayToken || !avplayActive || seq !== playSeq) return;
    if (channel.radio || channel.frame) return;
    var info = null;
    try { info = api.getCurrentStreamInfo ? api.getCurrentStreamInfo() : null; } catch (e) { info = null; }
    if (!info || !info.length) return;          /* engine cannot tell us */
    var hasVideo = false;
    for (var i = 0; i < info.length; i++) {
      var type = String((info[i] && info[i].type) || '').toUpperCase();
      if (type === 'VIDEO') hasVideo = true;
    }
    if (hasVideo) return;
    if (!window.MediaSource || !window.MediaSource.isTypeSupported('video/mp4; codecs="avc1.4d4028,mp4a.40.2"')) return;
    stopAvplay();
    triedHls = true;
    startHls(String(channel.url).split('|')[0]);
    toast('No video track — trying the software player');
  }

  function playChannel(channel) {
    if (!channel) return;
    var seq = ++playSeq;
    current = channel;
    triedHls = false;
    avplayTried = false;
    diagOpen = false;
    clearVideoWatch();
    splash.className = 'splash hidden';
    if (hls) { hls.destroy(); hls = null; }
    stopAvplay();

    try {
      player.pause();
      player.removeAttribute('src');
      player.load();
    } catch (e) { /* ignore */ }

    /* Some broadcasters lock their CDN to their own web player (Referer
       header). Those channels point at the broadcaster's player page, which
       runs in this iframe with the right context. The website is cropped to
       its video area by #playerFrame, so hide the list and let it fill the
       screen. */
    if (channel.frame && IS_TIZEN) {
      stopFrame();
      frameEl.src = channel.frame;
      frameEl.style.display = 'block';
      frameMode = true;
      setPanel(false);
      render();
      toast('Playing ' + channel.name + ' · BACK for the list');
      return;
    }
    stopFrame();

    render();
    toast('Playing ' + channel.name);

    resolveMediaUrl(channel.url, function (url) {
      if (seq !== playSeq) return;
      player.src = url;
      var promise = player.play();
      if (promise && promise.catch) promise.catch(function () {});
    });
  }

  function stopFrame() {
    if (!frameMode) return;
    frameMode = false;
    frameEl.style.display = 'none';
    frameEl.src = 'about:blank';
  }

  /* ------------------------------------------------------------ video watch
     Some HLS streams start audio-only in the HTML5 element (the Malaysian
     RTM channels do). Nothing can be read from the element to force the video
     track, so when no frame arrives shortly after "playing" hand the stream to
     AVPlay, which demuxes it itself. */
  function clearVideoWatch() {
    if (videoWatch) { clearTimeout(videoWatch); videoWatch = null; }
  }

  function watchVideo(channel, seq) {
    clearVideoWatch();
    if (!IS_TIZEN || avplayTried || channel.radio || channel.frame) return;
    videoWatch = setTimeout(function () {
      videoWatch = null;
      if (seq !== playSeq || current !== channel || avplayTried) return;
      if (player.paused || player.videoWidth > 0) return;
      avplayTried = true;
      if (startAvplay(channel)) toast('No picture yet — trying the TV video engine');
    }, 2800);
  }

  function zap(delta) {
    var items = filtered();
    if (!items.length) return;
    var next = focusIndex() + delta;
    if (next < 0) next = items.length - 1;
    if (next >= items.length) next = 0;
    setFocusIndex(next);
    playChannel(items[next]);
    updateFocus();
  }

  player.addEventListener('error', function () {
    if (!current) return;
    var mediaErr = player.error;
    if (!mediaErr) return;   /* error event fired while switching sources */
    var isHls = /\.m3u8(\?|$)/i.test(current.url);
    if (IS_TIZEN && !avplayTried) {
      /* The TV's HTML5 player rejected the container: retry natively. */
      avplayTried = true;
      if (startAvplay(current)) return;
    }
    if (isHls && !NATIVE_HLS && !IS_TIZEN && !triedHls) {
      /* Browser without native HLS: try hls.js. TV browsers never get here. */
      triedHls = true;
      startHls(current.url);
      return;
    }
    toast('Could not play ' + current.name + ' (media error ' + mediaErr.code + ')', true);
  });

  player.addEventListener('playing', function () {
    splash.className = 'splash hidden';
    if (current) watchVideo(current, playSeq);
  });

  player.addEventListener('loadedmetadata', function () {
    if (player.videoWidth > 0) clearVideoWatch();
  });

  /* ------------------------------------------------------------ panel */
  function setPanel(open) {
    panelOpen = open;
    panel.className = 'panel' + (open ? '' : ' hidden');
    setAvplayVisible(!open);
  }

  /* ------------------------------------------------------------ info button
     The remote has no usable INFO key, so the panel header carries a
     clickable "i": click it with a pointer, or walk up to it with the arrow
     keys. Either way it asks for the hidden-channels PIN. */
  infoBtn.addEventListener('click', function () {
    infoFocused = false;
    updateFocus();
    openPin(hiddenMode);
  });

  /* Pointer users can also pick a channel straight from the list. */
  listEl.addEventListener('click', function (event) {
    var row = event.target && event.target.closest ? event.target.closest('.row') : null;
    if (!row) return;
    var idx = parseInt(row.getAttribute('data-i'), 10);
    var channel = filtered()[idx];
    if (!channel) return;
    infoFocused = false;
    setFocusIndex(idx);
    playChannel(channel);
    updateFocus();
  });

  /* ------------------------------------------------------------ hidden */
  function openPin(hiding) {
    pinOpen = true;
    pinValue = '';
    pinHiding = hiding;
    pinTitle.textContent = hiding ? 'Hide hidden channels' : 'Show hidden channels';
    pinError.textContent = '';
    updatePinDots();
    pinEl.className = 'overlay';
    setAvplayVisible(false);
  }

  function closePin() {
    pinOpen = false;
    pinEl.className = 'overlay hidden';
    setAvplayVisible(!panelOpen);
  }

  function updatePinDots() {
    var dots = pinDots.querySelectorAll('i');
    for (var i = 0; i < dots.length; i++) {
      dots[i].className = i < pinValue.length ? 'on' : '';
    }
  }

  function submitPin() {
    if (pinValue === HIDDEN_PIN) {
      closePin();
      if (pinHiding) {
        exitHidden();
      } else {
        enterHidden();
      }
    } else {
      pinError.textContent = 'Wrong PIN. Try again.';
      pinValue = '';
      updatePinDots();
    }
  }

  function enterHidden() {
    if (hiddenChannels.length) {
      hiddenMode = true;
      applyMode('Hidden channels');
      return;
    }

    var cached = null;
    try { cached = JSON.parse(localStorage.getItem(HIDDEN_CACHE_KEY) || 'null'); } catch (e) { cached = null; }
    if (cached && cached.length) {
      hiddenChannels = cached;
      hiddenMode = true;
      applyMode('Hidden channels (cached)');
    }

    toast('Loading hidden channels…');
    Promise.all(HIDDEN_URLS.map(function (url) {
      return fetchText(url).then(function (text) {
        return parseM3U(text).map(function (c) { c.section = 'HIDDEN'; return c; });
      }).catch(function () { return null; });
    })).then(function (lists) {
      var merged = [];
      var seen = {};
      lists.forEach(function (list) {
        (list || []).forEach(function (c) {
          if (!seen[c.url]) { seen[c.url] = true; merged.push(c); }
        });
      });
      if (!merged.length) {
        if (!hiddenChannels.length) toast('Could not load the hidden playlists', true);
        return;
      }
      hiddenChannels = merged;
      hiddenMode = true;
      try { localStorage.setItem(HIDDEN_CACHE_KEY, JSON.stringify(merged)); } catch (e) { /* quota */ }
      applyMode('Hidden: ' + merged.length + ' channels');
    });
  }

  function exitHidden() {
    hiddenMode = false;
    applyMode('Main playlist');
  }

  function applyMode(message) {
    focusTv = 0;
    infoFocused = false;
    current = null;
    clearVideoWatch();
    stopAvplay();
    stopFrame();
    try { player.pause(); player.removeAttribute('src'); player.load(); } catch (e) { /* ignore */ }
    render();
    if (message) toast(message);
    var items = filtered();
    if (items.length) playChannel(items[0]);
  }

  function noteInfoClick() {
    var now = Date.now();
    infoClicks = (now - infoLast > 2500) ? 1 : infoClicks + 1;
    infoLast = now;
    if (infoClicks >= 3) {
      infoClicks = 0;
      openPin(hiddenMode);
    }
  }

  /* ------------------------------------------------------------ keys */
  function onKey(event) {
    var code = event.keyCode;

    if (diagOpen) {
      diagOpen = false;
      splash.className = 'splash hidden';
      event.preventDefault();
      return;
    }

    if (pinOpen) {
      if (code >= 48 && code <= 57) { addPinDigit(String.fromCharCode(code)); }
      else if (code >= 96 && code <= 105) { addPinDigit(String.fromCharCode(code - 48)); }
      else if (code === 13) { submitPin(); }
      else if (code === 10009 || code === 27) { closePin(); }
      event.preventDefault();
      return;
    }

    switch (code) {
      case 38: moveFocus(-1); break;
      case 40: moveFocus(1); break;
      case 37: if (!infoFocused) zap(-1); break;
      case 39: if (!infoFocused) zap(1); break;
      case 13:
        if (bootFailed) { boot(); return; }
        if (infoFocused) { openPin(hiddenMode); break; }
        if (!panelOpen) { setPanel(true); return; }
        playChannel(filtered()[focusIndex()]);
        break;
      case 10009:
      case 27:
        setPanel(!panelOpen);
        break;
      case 457:
        noteInfoClick();
        break;
      case 415:
      case 10252:
        if (frameMode) return;
        var avplay = avplayApi();
        if (avplayActive && avplay) {
          try {
            if (player.dataset.avpaused === '1') {
              avplay.play(); player.dataset.avpaused = '0'; toast('Resume');
            } else {
              avplay.pause(); player.dataset.avpaused = '1'; toast('Paused');
            }
          } catch (e) { /* ignore */ }
        } else if (player.paused) {
          player.play(); toast('Resume');
        } else {
          player.pause(); toast('Paused');
        }
        break;
      default:
        return;
    }
    event.preventDefault();
  }

  function addPinDigit(digit) {
    if (pinValue.length >= 4) return;
    pinValue += digit;
    pinError.textContent = '';
    updatePinDots();
    if (pinValue.length === 4) submitPin();
  }

  document.addEventListener('keydown', onKey);

  /* ------------------------------------------------------------ boot */
  function boot() {
    bootFailed = false;
    splash.className = 'splash';
    splashText.textContent = 'Loading playlist…';

    fetchText(PLAYLIST_URL).then(function (text) {
      var parsed = parseM3U(text);
      if (!parsed.length) throw new Error('empty playlist');
      mainChannels = parsed;
      hiddenMode = false;
      current = null;
      splash.className = 'splash hidden';
      render();
      toast('Ready — press OK to play');
    }).catch(function (error) {
      bootFailed = true;
      splashText.textContent = 'Could not load the playlist (' + error.message +
        '). Check the TV internet connection, then press OK to retry.';
    });
  }

  function registerKeys() {
    try {
      if (typeof tizen === 'undefined' || !tizen.tvinputdevice) return;
      ['MediaPlay', 'MediaPause', 'MediaPlayPause', 'Info', 'Exit'].forEach(function (name) {
        try { tizen.tvinputdevice.registerKey(name); } catch (e) { /* not available */ }
      });
      ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9'].forEach(function (name) {
        try { tizen.tvinputdevice.registerKey(name); } catch (e) { /* not available */ }
      });
    } catch (e) { /* not a Tizen runtime */ }
  }

  window.addEventListener('load', function () {
    registerKeys();
    boot();
  });
})();
