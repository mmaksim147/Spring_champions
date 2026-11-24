import time
from pathlib import Path
from io import BytesIO
import hashlib

import requests
from ddgs import DDGS
from PIL import Image, ImageOps

BASE_DIR = (
    Path(__file__).resolve().parent
    / "web-client/src/main/resources/static/images/products"
)

PRODUCTS = {
    "cpu": {
        "AMD Ryzen 5 7600X": "ryzen-5-7600x",
        "AMD Ryzen 7 7700X": "ryzen-7-7700x",
        "AMD Ryzen 7 7800X3D": "ryzen-7-7800x3d",
        "AMD Ryzen 9 7900X": "ryzen-9-7900x",
        "AMD Ryzen 5 5600": "ryzen-5-5600",
        "Intel Core i5-12400F": "core-i5-12400f",
        "Intel Core i5-13400F": "core-i5-13400f",
        "Intel Core i7-13700K": "core-i7-13700k"
    },
    "gpu": {
        "NVIDIA GeForce RTX 4060": "rtx-4060",
        "MSI GeForce RTX 4060 Ti": "rtx-4060-ti",
        "ASUS GeForce RTX 4070 SUPER": "rtx-4070-super",
        "Gigabyte GeForce RTX 4080 SUPER": "rtx-4080-super",
        "AMD Radeon RX 7600": "rx-7600",
        "Sapphire Radeon RX 7800 XT": "rx-7800-xt"
    },
    "motherboard": {
        "MSI B650 Gaming Plus WiFi": "msi-b650-gaming-plus",
        "ASUS TUF Gaming B650-PLUS": "asus-tuf-b650-plus",
        "Gigabyte B650M DS3H": "gigabyte-b650m-ds3h",
        "ASUS PRIME B760M-K": "asus-prime-b760m-k",
        "MSI PRO B760-P": "msi-pro-b760-p",
        "Gigabyte Z790 AORUS ELITE": "gigabyte-z790-aorus-elite"
    },
    "ram": {
        "Kingston Fury Beast 16GB DDR5": "kingston-fury-16gb-ddr5",
        "Kingston Fury Beast 32GB DDR5": "kingston-fury-32gb-ddr5",
        "Corsair Vengeance 32GB DDR5": "corsair-vengeance-32gb-ddr5",
        "G.Skill Trident Z5 32GB": "gskill-trident-z5-32gb",
        "Crucial 16GB DDR4": "crucial-16gb-ddr4",
        "Kingston Fury Beast 32GB DDR4": "kingston-fury-32gb-ddr4"
    },
    "psu": {
        "Corsair RM750e 750W": "corsair-rm750e",
        "be quiet Pure Power 12 M 850W": "bequiet-pure-power-850w",
        "MSI MAG A650BN 650W": "msi-mag-a650bn",
        "DeepCool PQ1000M 1000W": "deepcool-pq1000m"
    },
    "case": {
        "DeepCool CC560": "deepcool-cc560",
        "NZXT H5 Flow": "nzxt-h5-flow",
        "Corsair 4000D Airflow": "corsair-4000d-airflow",
        "Cooler Master Q300L": "cooler-master-q300l"
    },
    "storage": {
        "Samsung 980 PRO 1TB": "samsung-980-pro",
        "Samsung 990 EVO 2TB": "samsung-990-evo",
        "WD Black SN850X 1TB": "wd-black-sn850x",
        "Kingston NV2 1TB": "kingston-nv2"
    },
    "cooler": {
        "DeepCool AK400": "deepcool-ak400",
        "DeepCool AK620": "deepcool-ak620",
        "Noctua NH-D15": "noctua-nh-d15",
        "Arctic Liquid Freezer III 360": "arctic-liquid-freezer-iii-360"
    }
}

PRODUCTS["motherboard"]["ASUS B760M DDR4"] = "asus-b760m"

PRODUCTS["motherboard"]["MSI B650 Gaming Plus"] = "msi-b650-gaming-plus-old"

PRODUCTS["ram"]["Kingston Fury 16GB DDR5"] = "kingston-fury-16gb-ddr5"

HEADERS = {
    "User-Agent": "Mozilla/5.0"
}


def image_hash(image):
    small = image.convert("L").resize((8, 8))
    pixels = list(small.getdata())
    average = sum(pixels) / len(pixels)

    return tuple(pixel > average for pixel in pixels)


def is_duplicate(new_hash, hashes):
    for old_hash in hashes:
        difference = 0

        for a, b in zip(new_hash, old_hash):
            if a != b:
                difference += 1

        if difference <= 6:
            return True

    return False


def download_images(name, folder, filename):
    hashes = []
    existing = []

    for i in range(1, 4):
        suffix = "" if i == 1 else f"-{i}"
        path = folder / f"{filename}{suffix}.webp"

        if path.exists():
            try:
                with Image.open(path) as image:
                    hashes.append(image_hash(image))

                existing.append(path)
            except Exception:
                path.unlink()

    if len(existing) >= 3:
        print(f"[SKIP] {name}: 3/3")
        return

    queries = [
        f"{name} product white background",
        f"{name} product different angles",
        f"{name} product close up"
    ]

    for query in queries:
        if len(existing) >= 3:
            break

        results = []

        for attempt in range(3):
            try:
                results = DDGS(timeout=25).images(
                    query=query,
                    max_results=20
                )

                if results:
                    break

            except Exception as e:
                print(f"[RETRY] {name}: {e}")
                time.sleep(5)

        for result in results:
            if len(existing) >= 3:
                break

            try:
                response = requests.get(
                    result["image"],
                    headers=HEADERS,
                    timeout=20
                )

                response.raise_for_status()

                image = Image.open(BytesIO(response.content))
                image = ImageOps.exif_transpose(image)

                if image.width < 300 or image.height < 300:
                    continue

                image = image.convert("RGBA")

                image.thumbnail(
                    (860, 560),
                    Image.Resampling.LANCZOS
                )

                background = Image.new(
                    "RGB",
                    (900, 600),
                    "white"
                )

                x = (900 - image.width) // 2
                y = (600 - image.height) // 2

                background.paste(image, (x, y), image)

                new_hash = image_hash(background)

                if is_duplicate(new_hash, hashes):
                    continue

                for i in range(1, 4):
                    suffix = "" if i == 1 else f"-{i}"
                    path = folder / f"{filename}{suffix}.webp"

                    if not path.exists():
                        break

                background.save(
                    path,
                    "WEBP",
                    quality=85
                )

                hashes.append(new_hash)
                existing.append(path)

                print(f"[OK] {name}: {len(existing)}/3")

            except Exception as e:
                print(f"[ERROR] {e}")

        time.sleep(3)

    print(f"[RESULT] {name}: {len(existing)}/3")


def main():
    for category, products in PRODUCTS.items():
        folder = BASE_DIR / category
        folder.mkdir(parents=True, exist_ok=True)

        for name, filename in products.items():
            download_images(name, folder, filename)
            time.sleep(2)

    files = list(BASE_DIR.rglob("*.webp"))

    print("\n========== РЕЗУЛЬТАТ ==========")
    print(f"Всего фотографий: {len(files)}")
    print("Целевое количество: 135")


if __name__ == "__main__":
    main()