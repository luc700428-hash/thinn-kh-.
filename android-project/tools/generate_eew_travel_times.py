"""Build the offline S-arrival table; development dependency: ObsPy 1.5.1.

Numerical output only. No ObsPy/TauP implementation is bundled in the app.
IASP91 is a global 1-D reference model, not a calibrated Taiwan alert service.
Run from android-project with python3 tools/generate_eew_travel_times.py.
"""
from pathlib import Path
import json
import random
import obspy
from obspy.taup import TauPyModel

ROOT = Path(__file__).resolve().parents[1]
DEPTHS = [0, 1, 2, 5, 8, 10, 15, 20, 25, 30, 35, 40, 50, 60, 75, 100,
          125, 150, 175, 200, 225, 250, 275, 300, 350, 400, 450, 500, 550, 600, 650, 700]
DISTANCES = list(range(0, 601, 10)) + list(range(625, 1201, 25))
MODEL = TauPyModel(model="iasp91")


def arrival(depth, distance):
    rays = MODEL.get_travel_times(depth, distance / 111.19492664455873,
                                 phase_list=["s", "S", "Sn"])
    return min(float(ray.time) for ray in rays)


def main():
    rows = []
    for depth in DEPTHS:
        rows.append([round(arrival(depth, distance) * 100) for distance in DISTANCES])
        print("Calculated depth", depth, flush=True)
    output = ['package io.atmos.weather.earthquake', '',
              '/** Generated numerical IASP91 first S/s/Sn arrivals, in centiseconds.',
              ' * Rebuild: tools/generate_eew_travel_times.py. See docs/EEW_LOCAL_PREDICTION.md.',
              ' * The UI must identify this as a local model estimate, never source data. */',
              'internal object SeismicTravelTimeTable {',
              '    val depths = doubleArrayOf(' + ','.join(str(x)+'.0' for x in DEPTHS) + ')',
              '    val distances = doubleArrayOf(' + ','.join(str(x)+'.0' for x in DISTANCES) + ')',
              '    val seconds = arrayOf(' + ','.join('row'+str(i)+'()' for i in range(len(rows))) + ')']
    for i, row in enumerate(rows):
        output.append('    private fun row'+str(i)+'() = intArrayOf('+','.join(map(str,row))+')')
    output.append('}')
    (ROOT/'app/src/main/java/io/atmos/weather/earthquake/SeismicTravelTimeTable.kt').write_text('\n'.join(output)+'\n')
    # Holdout points are independent TauP evaluations, not interpolated table values.
    rng = random.Random(9210403)
    points = [(18, 143), (40, 283), (8, 212), (0, 7), (20, 125), (35, 125)]
    points += [(rng.uniform(0, 700), rng.uniform(0, 1200)) for _ in range(96)]
    fixture = {'obspy_version': obspy.__version__, 'model': 'iasp91', 'phases': ['s','S','Sn'],
               'reference': 'https://www.seis.sc.edu/webtaup/doc/taup_table.html',
               'points': [{'depth': d, 'distance': r, 'seconds': arrival(d,r)} for d,r in points]}
    path=ROOT/'app/src/test/resources/eew/iasp91-holdout.json'
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(fixture, indent=2)+'\n')


if __name__ == '__main__':
    main()
