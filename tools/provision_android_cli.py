#!/usr/bin/env python3
"""Install official Linux Android CLI tools into an explicit temporary CI directory."""
import argparse
import hashlib
import shutil
import tempfile
from pathlib import Path
import urllib.request
import xml.etree.ElementTree as ET
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    root = ET.fromstring(urllib.request.urlopen(
        'https://dl.google.com/android/repository/repository2-1.xml', timeout=60).read())
    package = next(item for item in root.findall('remotePackage')
                   if item.get('path') == 'cmdline-tools;latest')
    archive = next(item for item in package.findall('./archives/archive')
                   if item.findtext('host-os') == 'linux')
    complete = archive.find('complete')
    checksum = complete.find('checksum')
    url = 'https://dl.google.com/android/repository/' + complete.findtext('url')
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    destination = output / 'tools.zip'
    with urllib.request.urlopen(url, timeout=120) as response, destination.open('wb') as target:
        while True:
            chunk = response.read(1024 * 1024)
            if not chunk:
                break
            target.write(chunk)
    algorithm = checksum.get('type', 'sha1').replace('-', '').lower()
    digest = hashlib.new(algorithm)
    with destination.open('rb') as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(chunk)
    if digest.hexdigest() != checksum.text.strip():
        raise RuntimeError('Official SDK archive checksum mismatch')
    with zipfile.ZipFile(destination) as source:
        for entry in source.infolist():
            path = (output / entry.filename).resolve()
            if output not in path.parents:
                raise RuntimeError('Unexpected SDK archive path')
        with tempfile.TemporaryDirectory() as directory:
            source.extractall(directory)
            shutil.copytree(Path(directory) / 'cmdline-tools', output, dirs_exist_ok=True)
    for binary in (output / 'bin').iterdir():
        if binary.is_file():
            binary.chmod(0o755)
    print('Official SDK command-line tools installed and checksum verified')


if __name__ == '__main__':
    main()
