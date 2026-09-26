"""Shared palette. Every material is a 7-step ramp, darkest first. Shadows lean cool (towards indigo/violet),
highlights lean warm, so neighbouring materials sit in one light. Index 3 is the base tone of each ramp."""


def hx(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def _r(*hexes):
    return [hx(h) for h in hexes]


OUTLINE = hx('#141224')        # the one line colour: deep ink with a violet cast
SHADOW = hx('#07091a')

RAMPS = {
    # metals
    'iron':      _r('#181a28', '#2a2e42', '#434a5f', '#656e84', '#9098aa', '#c3cad5', '#eef1f5'),
    'copper':    _r('#2a1410', '#522619', '#7e3f25', '#a85c33', '#d0804c', '#eaa877', '#fbd9b2'),
    'brass':     _r('#2a1c0c', '#553913', '#89601f', '#b98c32', '#dcb857', '#f1dc95', '#fff6d2'),
    'verdigris': _r('#0f2520', '#1c4037', '#2c6052', '#3e8e7e', '#5db6a1', '#8ed9c8', '#cbf3e7'),
    # organic
    'bone':      _r('#3a2e2a', '#6c5a4c', '#9c8a73', '#c3b39a', '#e6dcc8', '#f4eee1', '#fffdf6'),
    'paper':     _r('#40342c', '#76644f', '#a8957a', '#cfc0a3', '#ebe1cc', '#f7f1e3', '#fffef8'),
    'leather':   _r('#1e110c', '#3a2117', '#5b3724', '#7b5036', '#9d6d49', '#c19271', '#e1bea0'),
    'wood':      _r('#22160e', '#42301c', '#654a2a', '#8a683c', '#ab8751', '#c9a86f', '#e5cf9c'),
    'gel':       _r('#0c2a26', '#16473f', '#23705f', '#36a088', '#5fd0b4', '#9cf0d9', '#e2fff6'),
    # cloth
    'indigo':    _r('#0d1027', '#1b2349', '#2a3567', '#3c4989', '#5566ac', '#7c8dca', '#aab8e6'),
    'ink':       _r('#08091a', '#10142d', '#181e3f', '#222a52', '#2f3a69', '#434f82', '#5d6a9e'),
    # stone
    'mute':      _r('#1a1c23', '#2b2e37', '#3a3e48', '#525764', '#6e7380', '#8e939f', '#b4b8c1'),
    'navy':      _r('#0c0f23', '#161b37', '#212a4d', '#2f3965', '#424e7f', '#5b699b', '#8390bc'),
    'deep':      _r('#0a0a16', '#12121f', '#1b1b2c', '#26263b', '#34344d', '#48485f', '#626279'),
    # memory / crystal
    'amethyst':  _r('#1f1033', '#3a1d5b', '#5c3695', '#7d52bf', '#aa78e6', '#cfaaf6', '#f2e3ff'),
    'scar':      _r('#1c0c2c', '#3a1650', '#5c2a86', '#8446bf', '#b06ae6', '#dca8ff', '#fbe8ff'),
    'magenta':   _r('#330a2c', '#5e1450', '#932680', '#c440aa', '#e868d6', '#f7a6ea', '#ffe0fa'),
    'echo':      _r('#46182f', '#782c58', '#b24e87', '#df78ae', '#ffa8d6', '#ffd4ea', '#fff2f9'),
    'ember':     _r('#3a120c', '#6e2215', '#a83a21', '#e07a4a', '#f7a66e', '#ffd2a6', '#fff0dc'),
    'red':       _r('#2c0a0c', '#561318', '#8c2025', '#c23a34', '#e2604e', '#f59a82', '#ffd0c2'),
    'pale':      _r('#4a4c66', '#6c6f8a', '#9497b0', '#b9bcd0', '#dadcea', '#eeeff7', '#ffffff'),
    'white':     _r('#8a8a8a', '#a8a8a8', '#c4c4c4', '#dcdcdc', '#eeeeee', '#f8f8f8', '#ffffff'),
    'glass':     _r('#1a3040', '#2a4c60', '#3f6c80', '#5c90a4', '#86b8c8', '#b8dde6', '#eefbff'),
    'redstone':  _r('#2a0606', '#520a0a', '#7e1010', '#aa1a14', '#d8321f', '#f7684a', '#ffb09a'),
    'gold':      _r('#2d1d06', '#5a3a0c', '#8c6014', '#bf8b1f', '#e8b448', '#f7da86', '#fff4cc'),
}

# Accent colours shared with gameplay code (tempers are the Temper.rgb() values; tags follow the tag icons).
TEMPER = {
    'silence': hx('#B8C6DC'), 'death': hx('#B6A2E8'), 'fire': hx('#FF9A5C'), 'fall': hx('#7FE0CF'), 'explosion': hx('#FF5E4E'),
}


def ramp(name):
    return RAMPS[name]


def c(name, i=3):
    r = RAMPS[name]
    return r[max(0, min(len(r) - 1, i))]


def rgba(rgb, a=255):
    return (rgb[0], rgb[1], rgb[2], a)


def mix(a, b, t):
    return tuple(int(round(int(a[i]) + (int(b[i]) - int(a[i])) * t)) for i in range(3))
