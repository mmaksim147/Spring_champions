
(() => {
    "use strict";

    const normalize = value =>
        String(value ?? "")
            .toLocaleLowerCase("ru-RU")
            .replace(/\s+/g, " ")
            .trim();

    const configurations = [
        ["componentLiveSearch", "componentsTable"],
        ["categoryLiveSearch", "categoriesTable"],
        ["userSearch", "usersTable"]
    ];

    configurations.forEach(([inputId, tableId]) => {
        const input = document.getElementById(inputId);
        const table = document.getElementById(tableId);

        if (!input || !table) return;

        const isComponents = tableId === "componentsTable";

        const brandFilter = isComponents
            ? document.getElementById("componentBrandFilter")
            : null;

        const stockFilter = isComponents
            ? document.getElementById("componentStockFilter")
            : null;

        const minPrice = isComponents
            ? document.getElementById("componentMinPrice")
            : null;

        const maxPrice = isComponents
            ? document.getElementById("componentMaxPrice")
            : null;

        const resetButton = isComponents
            ? document.getElementById("componentResetFilters")
            : null;

        const status = isComponents
            ? document.getElementById("componentFilterStatus")
            : null;

        const counter = document.getElementById("componentCounter");
        const noResults = document.getElementById("componentNoResults");

        const rows = Array.from(
            table.querySelectorAll("tbody tr")
        );

        function parsePrice(value) {
            const number = String(value ?? "")
                .replace(/\s/g, "")
                .replace("₽", "")
                .replace(",", ".");

            return Number(number);
        }

        function rowText(row) {
            const cellText = Array.from(row.cells)
                .map(cell => cell.textContent)
                .join(" ");

            const inputValues = Array.from(
                row.querySelectorAll(
                    "input:not([type=hidden]), select"
                )
            ).map(field => {
                if (field.tagName === "SELECT") {
                    return field.selectedOptions[0]?.textContent ?? "";
                }

                return field.value ?? "";
            });

            return normalize([
                cellText,
                ...inputValues,
                row.dataset.userSearch ?? ""
            ].join(" "));
        }

        const records = rows.map(row => {
            const cells = row.cells;

            return {
                row,
                brand: isComponents
                    ? normalize(cells[1]?.textContent)
                    : "",
                brandLabel: isComponents
                    ? cells[1]?.textContent.trim() ?? ""
                    : "",
                price: isComponents
                    ? parsePrice(cells[2]?.textContent)
                    : NaN,
                stock: isComponents
                    ? Number(cells[3]?.textContent.trim())
                    : NaN
            };
        });

        if (isComponents && brandFilter) {
            const brands = new Map();

            records.forEach(record => {
                if (record.brand) {
                    brands.set(record.brand, record.brandLabel);
                }
            });

            Array.from(brands.entries())
                .sort((a, b) => a[1].localeCompare(b[1], "ru"))
                .forEach(([value, label]) => {
                    const option = document.createElement("option");
                    option.value = value;
                    option.textContent = label;
                    brandFilter.append(option);
                });
        }

        let genericCounter = null;
        let genericEmpty = null;

        if (!isComponents) {
            genericCounter = document.createElement("p");
            genericCounter.className = "admin-results-count";
            genericCounter.setAttribute("aria-live", "polite");

            table.closest(".admin-table-wrap, .table-wrap")
                ?.before(genericCounter);

            genericEmpty = document.createElement("p");
            genericEmpty.className = "admin-no-results";
            genericEmpty.textContent =
                "По вашему запросу ничего не найдено.";
            genericEmpty.hidden = true;

            table.closest(".admin-table-wrap, .table-wrap")
                ?.after(genericEmpty);
        }

        function filterRows() {
            const terms = normalize(input.value)
                .split(" ")
                .filter(Boolean);

            const selectedBrand = brandFilter?.value ?? "";
            const selectedStock = stockFilter?.value ?? "";

            const minimum = minPrice && minPrice.value !== ""
                ? Number(minPrice.value)
                : null;

            const maximum = maxPrice && maxPrice.value !== ""
                ? Number(maxPrice.value)
                : null;

            let visible = 0;

            records.forEach(record => {
                const matchesText = terms.every(term =>
                    rowText(record.row).includes(term)
                );

                const matchesBrand =
                    !selectedBrand ||
                    record.brand === selectedBrand;

                const matchesStock =
                    !selectedStock ||
                    (selectedStock === "available"
                        ? record.stock > 0
                        : record.stock === 0);

                const matchesMinimum =
                    minimum === null ||
                    (Number.isFinite(record.price) &&
                        record.price >= minimum);

                const matchesMaximum =
                    maximum === null ||
                    (Number.isFinite(record.price) &&
                        record.price <= maximum);

                const matches =
                    matchesText &&
                    matchesBrand &&
                    matchesStock &&
                    matchesMinimum &&
                    matchesMaximum;

                record.row.hidden = !matches;

                if (matches) visible++;
            });

            const message =
                `Показано ${visible} из ${records.length}`;

            if (counter) counter.textContent = message;
            if (genericCounter) genericCounter.textContent = message;

            if (status) {
                status.textContent =
                    `${message} товаров на текущей странице`;
            }

            if (noResults) noResults.hidden = visible !== 0;
            if (genericEmpty) genericEmpty.hidden = visible !== 0;
        }

        input.addEventListener("input", filterRows);

        [brandFilter, stockFilter, minPrice, maxPrice]
            .filter(Boolean)
            .forEach(field => {
                field.addEventListener("input", filterRows);
                field.addEventListener("change", filterRows);
            });

        table.addEventListener("change", filterRows);
        table.addEventListener("input", filterRows);

        resetButton?.addEventListener("click", () => {
            input.value = "";

            if (brandFilter) brandFilter.value = "";
            if (stockFilter) stockFilter.value = "";
            if (minPrice) minPrice.value = "";
            if (maxPrice) maxPrice.value = "";

            filterRows();
        });

        filterRows();
    });
})();
