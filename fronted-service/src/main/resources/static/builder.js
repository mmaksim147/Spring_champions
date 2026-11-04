let categories = [];
let components = [];
let selectedComponents = {};

window.onload = async () => {
    const cats = await apiRequest('/categories');
    categories = cats.content || cats;

    const comps = await apiRequest('/components');
    components = comps.content || comps;

    renderSlots();
};

function renderSlots() {
    const container = document.getElementById('build-slots');
    container.innerHTML = '';

    categories.forEach(cat => {
        container.innerHTML += `
            <div class="build-slot" id="slot-${cat.id}">
                <div style="width: 150px;"><strong>${cat.name}</strong></div>
                <select onchange="selectComponent(${cat.id}, this.value)">
                    <option value="">-- Выберите деталь --</option>
                    ${components.filter(c => (c.categoryId === cat.id || c.category?.id === cat.id))
            .map(c => `<option value="${c.id}" data-price="${c.price}">${c.name} (${c.price} ₽)</option>`).join('')}
                </select>
            </div>
        `;
    });
}

function selectComponent(catId, compId) {
    if (compId) {
        const comp = components.find(c => c.id == compId);
        selectedComponents[catId] = comp;
        document.getElementById(`slot-${catId}`).classList.add('filled');
    } else {
        delete selectedComponents[catId];
        document.getElementById(`slot-${catId}`).classList.remove('filled');
    }
    updateSummary();
}

function updateSummary() {
    let total = 0;
    let html = '';
    Object.values(selectedComponents).forEach(c => {
        total += c.price;
        html += `<div style="margin-bottom: 5px;">• ${c.name}</div>`;
    });

    document.getElementById('selected-items-list').innerHTML = html || 'Пока ничего не выбрано';
    document.getElementById('total-price').innerText = total.toLocaleString('ru-RU') + ' ₽';
}

async function saveBuild() {
    if (Object.keys(selectedComponents).length === 0) return alert('Выберите хотя бы одну деталь');

    try {
        // 1. Создаем сборку
        const buildName = prompt('Введите название вашей сборки:', 'Моя сборка');
        if (!buildName) return;

        const buildRes = await apiRequest('/builds', 'POST', { name: buildName, userId: 1 }); // TODO: брать ID из токена
        const buildId = buildRes.id;

        // 2. Добавляем детали
        for (const comp of Object.values(selectedComponents)) {
            await apiRequest(`/builds/${buildId}/items`, 'POST', { componentId: comp.id, quantity: 1 });
        }

        alert('Сборка успешно сохранена!');
        window.location.href = '/profile.html';
    } catch (e) {
        alert('Ошибка сохранения сборки');
    }
}