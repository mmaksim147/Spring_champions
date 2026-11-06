(() => {
    "use strict";

    const list = document.getElementById("adminOrderList");
    const search = document.getElementById("orderSearch");
    const status = document.getElementById("orderStatus");
    const dateFrom = document.getElementById("orderDateFrom");
    const dateTo = document.getElementById("orderDateTo");
    const sort = document.getElementById("orderSort");
    const reset = document.getElementById("resetOrderFilters");
    const counter = document.getElementById("orderCounter");
    const empty = document.getElementById("filteredOrdersEmpty");
    const pagination = document.getElementById("adminOrderPagination");

    if (!search || !status || !dateFrom || !dateTo ||
        !sort || !reset || !counter || !pagination) {
        return;
    }

    const orders = list
        ? Array.from(list.querySelectorAll(".admin-order-card"))
        : [];

    const PAGE_SIZE = 8;
    let currentPage = 1;

    function normalize(value) {
        return String(value ?? "")
            .trim()
            .toLocaleLowerCase("ru-RU");
    }

    function numberValue(value) {
        const result = Number(value);
        return Number.isFinite(result) ? result : 0;
    }

    function orderDate(order) {
        const raw = order.dataset.date || "";
        return raw.slice(0, 10);
    }

    function orderTimestamp(order) {
        const raw = order.dataset.date || "";
        const parsed = Date.parse(raw);

        return Number.isFinite(parsed) ? parsed : 0;
    }

    function fillStatuses() {
        const statuses = new Set();

        orders.forEach(order => {
            const value = (order.dataset.status || "").trim();
            if (value) statuses.add(value);
        });

        Array.from(statuses)
            .sort((a, b) => a.localeCompare(b, "ru"))
            .forEach(value => {
                const option = document.createElement("option");
                option.value = value;
                option.textContent = value;
                status.append(option);
            });
    }

    function matchesSearch(order, query) {
        if (!query) return true;

        const searchableText = [
            order.dataset.id,
            order.dataset.user,
            order.dataset.status,
            order.dataset.date,
            order.dataset.price,
            order.textContent
        ].join(" ");

        return normalize(searchableText).includes(query);
    }

    function compareOrders(a, b) {
        switch (sort.value) {
            case "date-asc":
                return orderTimestamp(a) - orderTimestamp(b);

            case "price-desc":
                return numberValue(b.dataset.price)
                    - numberValue(a.dataset.price);

            case "price-asc":
                return numberValue(a.dataset.price)
                    - numberValue(b.dataset.price);

            case "id-desc":
                return numberValue(b.dataset.id)
                    - numberValue(a.dataset.id);

            case "id-asc":
                return numberValue(a.dataset.id)
                    - numberValue(b.dataset.id);

            case "date-desc":
            default:
                return orderTimestamp(b) - orderTimestamp(a);
        }
    }

    function getFilteredOrders() {
        const query = normalize(search.value);
        const selectedStatus = status.value;
        const from = dateFrom.value;
        const to = dateTo.value;

        return orders
            .filter(order => {
                if (!matchesSearch(order, query)) {
                    return false;
                }

                if (selectedStatus &&
                    order.dataset.status !== selectedStatus) {
                    return false;
                }

                const date = orderDate(order);

                if (from && (!date || date < from)) {
                    return false;
                }

                if (to && (!date || date > to)) {
                    return false;
                }

                return true;
            })
            .sort(compareOrders);
    }

    function makePageButton(label, page, disabled, active = false) {
        const button = document.createElement("button");

        button.type = "button";
        button.textContent = label;
        button.disabled = disabled;

        if (active) {
            button.classList.add("active");
            button.setAttribute("aria-current", "page");
        }

        button.addEventListener("click", () => {
            currentPage = page;
            render();

            document.querySelector(".orders-toolbar")
                ?.scrollIntoView({
                    behavior: "smooth",
                    block: "start"
                });
        });

        return button;
    }

    function renderPagination(totalPages) {
        pagination.replaceChildren();

        if (totalPages <= 1) return;

        pagination.append(
            makePageButton(
                "←",
                currentPage - 1,
                currentPage === 1
            )
        );

        const firstPage = Math.max(1, currentPage - 2);
        const lastPage = Math.min(totalPages, currentPage + 2);

        for (let page = firstPage; page <= lastPage; page++) {
            pagination.append(
                makePageButton(
                    String(page),
                    page,
                    false,
                    page === currentPage
                )
            );
        }

        pagination.append(
            makePageButton(
                "→",
                currentPage + 1,
                currentPage === totalPages
            )
        );
    }

    function render() {
        const filtered = getFilteredOrders();
        const totalPages = Math.max(
            1,
            Math.ceil(filtered.length / PAGE_SIZE)
        );

        currentPage = Math.min(
            Math.max(1, currentPage),
            totalPages
        );

        const start = (currentPage - 1) * PAGE_SIZE;
        const visible = filtered.slice(start, start + PAGE_SIZE);

        orders.forEach(order => {
            order.hidden = true;
        });

        // Переставляем карточки согласно выбранной сортировке.
        if (list) {
            filtered.forEach(order => list.append(order));
        }

        visible.forEach(order => {
            order.hidden = false;
        });

        counter.textContent =
            `Показано ${visible.length} из ${filtered.length}` +
            ` · Всего заказов: ${orders.length}`;

        if (empty) {
            empty.hidden = filtered.length !== 0 || orders.length === 0;
        }

        renderPagination(
            filtered.length === 0 ? 0 : totalPages
        );
    }

    function updateFilters() {
        currentPage = 1;
        render();
    }

    [search, status, dateFrom, dateTo, sort].forEach(field => {
        field.addEventListener("input", updateFilters);
        field.addEventListener("change", updateFilters);
    });

    reset.addEventListener("click", () => {
        search.value = "";
        status.value = "";
        dateFrom.value = "";
        dateTo.value = "";
        sort.value = "date-desc";

        updateFilters();
    });

    fillStatuses();
    render();
})();