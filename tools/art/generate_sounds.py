#!/usr/bin/env python3
"""Procedural placeholder OGGs for Mnemolith (Z1). Distinct tones, not AAA.

Everything here is synthesised from sine/saw/noise math: no samples, no third-party audio.
``--hollows`` writes only the Memory Hollows sounds (stage 2), leaving the older files untouched.
``--faded`` writes only the stage 3 sounds (the faded and the lectern reading).
"""
import math, os, struct, subprocess, sys, wave

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT = os.path.join(REPO, 'src', 'main', 'resources', 'assets', 'mnemolith', 'sounds')
SR = 22050

def save_ogg(name, samples, stream=False):
    os.makedirs(OUT, exist_ok=True)
    path_wav = f'/tmp/{name}.wav'
    path_ogg = os.path.join(OUT, f'{name}.ogg')
    with wave.open(path_wav, 'w') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        frames = bytearray()
        for s in samples:
            v = max(-1.0, min(1.0, s))
            frames += struct.pack('<h', int(v * 30000))
        w.writeframes(frames)
    q = '3' if stream else '4'
    subprocess.check_call(['ffmpeg', '-y', '-i', path_wav, '-c:a', 'libvorbis', '-q:a', q, path_ogg],
                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    print(name, os.path.getsize(path_ogg))

def tone(freq, dur, vol=0.5, attack=0.01, release=0.05, wave='sine'):
    n = int(SR * dur); out = []
    for i in range(n):
        t = i / SR
        env = 1.0
        if t < attack: env = t / attack
        elif t > dur - release: env = max(0.0, (dur - t) / release)
        phase = 2 * math.pi * freq * t
        if wave == 'sine': s = math.sin(phase)
        elif wave == 'square': s = 1.0 if math.sin(phase) >= 0 else -1.0
        elif wave == 'saw': s = 2 * ((freq * t) % 1.0) - 1.0
        else: s = math.sin(phase * 12.9898) * math.cos(phase * 78.233)
        out.append(s * vol * env)
    return out

def mix(*tracks):
    n = max(len(t) for t in tracks)
    out = [0.0] * n
    for t in tracks:
        for i, s in enumerate(t): out[i] += s
    return out

def pad(samples, total):
    return samples[:total] if len(samples) >= total else samples + [0.0] * (total - len(samples))

def music_loop(name, base_freq, seconds=12.0, dark=True):
    n = int(SR * seconds); out = []
    for i in range(n):
        t = i / SR
        lfo = 0.5 + 0.5 * math.sin(2 * math.pi * 0.08 * t)
        s = 0.0
        for mul, vol in ((1.0, 0.22), (1.5, 0.12), (2.0, 0.08), (0.5, 0.18)):
            s += math.sin(2 * math.pi * base_freq * mul * t) * vol * lfo
        if dark:
            s += math.sin(2 * math.pi * (base_freq * 0.25) * t) * 0.15
            s += math.sin(t * 1234.5) * math.cos(t * 987.6) * 0.04 * lfo
        edge = min(t, seconds - t, 1.5) / 1.5
        out.append(s * max(0.0, min(1.0, edge)))
    save_ogg(name, out, stream=True)

def bell(freq, dur, vol=0.3, decay=2.5):
    """A struck glass bell: a few inharmonic partials with an exponential tail."""
    n = int(SR * dur); out = []
    partials = ((1.0, 1.0), (2.76, 0.45), (5.4, 0.2), (8.93, 0.08))
    for i in range(n):
        t = i / SR
        env = min(1.0, t / 0.004) * math.exp(-decay * t)
        s = sum(math.sin(2 * math.pi * freq * m * t) * a * math.exp(-decay * m * 0.35 * t) for m, a in partials)
        out.append(s * vol * env)
    return out


def at(samples, offset_s, total):
    """``samples`` placed ``offset_s`` seconds into a silent track of ``total`` samples."""
    start = int(offset_s * SR)
    out = [0.0] * total
    for i, v in enumerate(samples):
        if start + i < total:
            out[start + i] = v
    return out


def hollows_music(name, seconds=48.0):
    """Memory Hollows: a soft pad on D with slow glass-bell phrases. Original, generated here."""
    n = int(SR * seconds)
    pad_track = []
    for i in range(n):
        t = i / SR
        lfo = 0.55 + 0.45 * math.sin(2 * math.pi * 0.05 * t)
        s = 0.0
        for f, v in ((73.42, 0.16), (110.0, 0.10), (146.83, 0.07), (220.0, 0.035)):
            s += math.sin(2 * math.pi * f * t + 0.3 * math.sin(2 * math.pi * 0.11 * t)) * v
        edge = min(t, seconds - t, 3.0) / 3.0
        pad_track.append(s * lfo * max(0.0, min(1.0, edge)))
    # D dorian phrases, a note every 1.5 s with rests, each bell rings out
    notes = [587.33, 659.25, 698.46, 880.0, 783.99, 698.46, 659.25, 0,
             523.25, 587.33, 659.25, 587.33, 440.0, 0, 493.88, 587.33,
             698.46, 659.25, 587.33, 0, 880.0, 783.99, 659.25, 587.33,
             0, 440.0, 523.25, 587.33]
    tracks = [pad_track]
    for k, f in enumerate(notes):
        if f:
            tracks.append(at(bell(f, 3.5, 0.11, 1.6), 2.0 + k * 1.5, n))
    save_ogg(name, mix(*tracks), stream=True)


def hollows_sounds():
    # ambient additions: a single far glass chime, pitch varied in sounds.json
    save_ogg('hollows_chime', pad(bell(1174.66, 1.6, 0.18, 3.0), int(1.8 * SR)))
    # mood: a low breath under the turf, like a memory almost surfacing
    n = int(SR * 3.0); breath = []
    for i in range(n):
        t = i / SR
        env = math.sin(math.pi * t / 3.0) ** 2
        s = math.sin(2 * math.pi * 92.5 * t) * 0.18 + math.sin(2 * math.pi * 138.6 * t) * 0.08
        s += math.sin(t * 1834.1) * math.cos(t * 1213.7) * 0.05
        breath.append(s * env)
    save_ogg('hollows_mood', breath)
    # catching a flicker: a rising pair of bells and a short shimmer
    catch = mix(bell(880.0, 0.9, 0.2, 4.0), at(bell(1318.5, 0.8, 0.18, 4.5), 0.09, int(0.9 * SR)),
                at(tone(2637.0, 0.25, 0.05, 0.02, 0.2), 0.12, int(0.9 * SR)))
    save_ogg('flicker_catch', catch)
    hollows_music('music_memory_hollows')


def faded_sounds():
    # hurt: a cracked glass tap, a bell knocked off pitch with a burst of noise
    hurt = mix(bell(740.0, 0.35, 0.22, 9.0), at(bell(784.0, 0.3, 0.12, 10.0), 0.01, int(0.35 * SR)),
               tone(1500, 0.06, 0.12, 0.002, 0.04, 'noise'))
    save_ogg('faded_hurt', hurt)
    # death: three falling bells that thin out into a breath
    total = int(1.6 * SR)
    death = mix(at(bell(987.8, 1.2, 0.2, 3.5), 0.0, total), at(bell(740.0, 1.1, 0.17, 3.5), 0.18, total),
                at(bell(493.9, 1.0, 0.15, 3.0), 0.36, total), at(tone(220, 1.0, 0.06, 0.3, 0.6), 0.5, total))
    save_ogg('faded_death', death)
    # lectern reading: a soft page-turn noise and two low bells, like reading aloud in an empty hall
    total = int(1.4 * SR)
    read = mix(at(tone(3000, 0.12, 0.05, 0.01, 0.1, 'noise'), 0.0, total), at(bell(587.3, 1.1, 0.16, 3.0), 0.08, total),
               at(bell(440.0, 1.1, 0.13, 3.0), 0.3, total))
    save_ogg('lectern_replay', read)


def main():
    if len(sys.argv) > 1 and sys.argv[1] == '--hollows':
        hollows_sounds()
        return
    if len(sys.argv) > 1 and sys.argv[1] == '--faded':
        faded_sounds()
        return
    save_ogg('mite_ambient', mix(tone(1800, 0.08, 0.35, 0.005, 0.03, 'square'),
        pad(tone(2400, 0.05, 0.25, 0.002, 0.02, 'sine'), int(0.18*SR)) + tone(2100, 0.06, 0.3, 0.002, 0.02, 'square')))
    save_ogg('mite_hurt', tone(900, 0.12, 0.45, 0.005, 0.04, 'saw'))
    save_ogg('witness_ambient', mix(tone(110, 0.55, 0.35, 0.08, 0.2), tone(165, 0.55, 0.18, 0.1, 0.2), tone(55, 0.55, 0.2, 0.05, 0.25, 'saw')))
    save_ogg('witness_hurt', mix(tone(80, 0.2, 0.5, 0.01, 0.08, 'saw'), tone(140, 0.15, 0.3, 0.01, 0.05, 'square')))
    save_ogg('stalker_ambient', mix(tone(60, 0.7, 0.4, 0.15, 0.25, 'saw'), tone(90, 0.7, 0.15, 0.2, 0.3, 'noise'), tone(45, 0.7, 0.25, 0.1, 0.3)))
    save_ogg('stalker_hurt', tone(70, 0.25, 0.55, 0.01, 0.1, 'saw'))
    save_ogg('stalker_death', mix(tone(50, 0.6, 0.55, 0.02, 0.35, 'saw'), tone(35, 0.6, 0.35, 0.05, 0.4)))
    save_ogg('scar_ambient', mix(tone(95, 0.8, 0.3, 0.2, 0.3), tone(190, 0.8, 0.12, 0.25, 0.3), tone(48, 0.8, 0.2, 0.1, 0.4, 'saw')))
    save_ogg('scar_hurt', tone(130, 0.2, 0.5, 0.01, 0.08, 'square'))
    save_ogg('scar_death', mix(tone(70, 0.7, 0.5, 0.02, 0.4, 'saw'), tone(40, 0.7, 0.35, 0.05, 0.45)))
    save_ogg('scar_cast', mix(tone(220, 0.45, 0.4, 0.05, 0.15), tone(110, 0.45, 0.35, 0.08, 0.2, 'saw'), tone(440, 0.2, 0.2, 0.02, 0.1)))
    save_ogg('storm_gather', mix(tone(55, 1.2, 0.45, 0.3, 0.4, 'saw'), tone(82, 1.2, 0.25, 0.4, 0.4), tone(30, 1.2, 0.3, 0.2, 0.5, 'noise')))
    save_ogg('storm_wave', mix(tone(100, 0.5, 0.4, 0.05, 0.2, 'saw'), tone(200, 0.35, 0.25, 0.05, 0.15)))
    save_ogg('relay_tie', mix(tone(660, 0.15, 0.35, 0.01, 0.05), tone(990, 0.1, 0.25, 0.01, 0.04)))
    save_ogg('relay_untie', mix(tone(520, 0.15, 0.35, 0.01, 0.05), tone(390, 0.12, 0.25, 0.01, 0.05)))
    save_ogg('relay_hop', mix(tone(880, 0.12, 0.4, 0.005, 0.04, 'square'), tone(1320, 0.08, 0.25, 0.005, 0.03)))
    save_ogg('relay_break', mix(tone(200, 0.25, 0.45, 0.01, 0.1, 'saw'), tone(100, 0.25, 0.3, 0.02, 0.12, 'noise')))
    save_ogg('vault_chime', mix(tone(523, 0.35, 0.35, 0.01, 0.25), tone(784, 0.3, 0.25, 0.02, 0.22)))
    save_ogg('vault_draw', mix(tone(392, 0.4, 0.35, 0.05, 0.2), tone(294, 0.4, 0.25, 0.08, 0.2, 'saw')))
    save_ogg('vault_rupture', mix(tone(150, 0.45, 0.5, 0.01, 0.2, 'saw'), tone(80, 0.45, 0.35, 0.02, 0.25, 'noise')))
    save_ogg('echo_wake', mix(tone(440, 0.25, 0.3, 0.02, 0.15), tone(660, 0.2, 0.2, 0.03, 0.12)))
    music_loop('music_storm_gathering', 55.0, 14.0, True)
    music_loop('music_scar_fight', 70.0, 12.0, True)
    music_loop('music_disc_recollection', 65.0, 16.0, True)
    hollows_sounds()

if __name__ == '__main__':
    main()
