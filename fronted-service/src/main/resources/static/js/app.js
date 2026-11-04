let state = { categories: [], components: [], builds: [] };

async function initApp() {
    const token = localStorage.getItem('token');
    const root = document.getElementById('app-root');

    if (!token) {
        await loadPage('login', root);
        return;
    }

    root.innerHTML = `
        <div style="display: flex; flex-direction: column; height: 100vh;">
            <header>
                <div>
                    <h1>⚙️ PC Builder Admin</h1>
                    <nav>
                        <button class="active" onclick="switchTab('components', this)">Комплектующие</button>
                        <button onclick="switchTab('categories', this)">Категории</button>
                        <button onclick="switchTab('builds', this)">Сборки</button>
                    </nav>
                </div>
                <button class="danger" style="width: auto; padding: 8px 16px;" onclick="logout()">Выйти</button>
            </header>
            <div id="dynamic-content" style="flex: 1; overflow: hidden;"></div>
        </div>
    `;

    await loadPage('components', document.getElementById('dynamic-content'));
    await loadPage('categories', document.getElementById('dynamic-content'));
    await loadPage('builds', document.getElementById('dynamic-content'));

    await loadAllData();
    switchTab('components', document.querySelector('nav button'));
}

async function loadPage(pageName, container) {
    const res = await fetch(`/pages/${pageName}.html`);
    const html = await res.text();
    container.insertAdjacentHTML('beforeend', html);
}

function switchTab(tabId, btn) {
    document.querySelectorAll('.page-container').forEach(p => p.classList.remove('active'));
    document.querySelectorAll('nav button').forEach(b => b.classList.remove('active'));
    document.getElementById('page-' + tabId).classList.add('active');
    btn.classList.add('active');
}

async function loadAllData() {
    try {
        state.categories = await apiRequest('/categories');
        state.components = await apiRequest('/components');
        state.builds = await apiRequest('/builds');

        if(state.categories.content) state.categories = state.categories.content;
        if(state.components.content) state.components = state.components.content;
        if(state.builds.content) state.builds = state.builds.content;

        renderCategories();
        renderComponents();
        renderBuilds();
    } catch (e) { console.error(e); }
}

function renderCategories() {
    const grid = document.getElementById('categories-grid');
    const select = document.getElementById('comp-category');
    if(!grid) return;
    grid.innerHTML = '';
    select.innerHTML = '<option value="">Выбери категорию</option>';

    state.categories.forEach(c => {
        grid.innerHTML += `
            <div class="card">
                <h4>${c.name}</h4>
                <div class="actions"><button class="danger" onclick="deleteCategory(${c.id})">Удалить</button></div>
            </div>`;
        select.innerHTML += `<option value="${c.id}">${c.name}</option>`;
    });
}

function renderComponents() {
    const grid = document.getElementById('components-grid');
    if(!grid) return;
    grid.innerHTML = '';

    state.components.forEach(c => {
        const cat = state.categories.find(cat => cat.id === (c.categoryId || c.category?.id));
        const catName = cat ? cat.name : 'Без категории';
        grid.innerHTML += `
            <div class="card">
                <div class="brand">${c.manufacturer || 'Unknown'} | ${catName}</div>
                <h4>${c.name}</h4>
                <div class="price">${c.price} ₽</div>
                <div style="font-size: 12px; color: #94a3b8; margin-top: 5px;">На складе: ${c.stockQuantity || 0}</div>
                <div class="actions"><button class="danger" onclick="deleteComponent(${c.id})">Удалить</button></div>
            </div>`;
    });
}

function renderBuilds() {
    const grid = document.getElementById('builds-grid');
    if(!grid) return;
    grid.innerHTML = '';
    state.builds.forEach(b => {
        grid.innerHTML += `
            <div class="card" style="grid-column: span 2;">
                <h4>Сборка: ${b.name} (ID: ${b.id})</h4>
                <div class="price">Итого: ${b.totalPrice || 0} ₽</div>
                <div class="add-item-row">
                    <select id="add-item-${b.id}">
                        ${state.components.map(c => `<option value="${c.id}">${c.name} (${c.price} ₽)</option>`).join('')}
                    </select>
                    <button onclick="addItemToBuild(${b.id})">Добавить</button>
                </div>
                <div class="actions"><button class="danger" onclick="deleteBuild(${b.id})">Удалить</button></div>
            </div>`;
    });
}

async function createCategory() {
    const name = document.getElementById('cat-name').value;
    if (!name) return alert('Введи название');
    await apiRequest('/categories', 'POST', { name });
    document.getElementById('cat-name').value = '';
    loadAllData();
}
async function deleteCategory(id) {
    if(!confirm('Удалить?')) return;
    await apiRequest('/categories/' + id, 'DELETE');
    loadAllData();
}
async function createComponent() {
    const name = document.getElementById('comp-name').value;
    const manufacturer = document.getElementById('comp-manuf').value;
    const price = parseFloat(document.getElementById('comp-price').value);
    const stockQuantity = parseInt(document.getElementById('comp-stock').value);
    const categoryId = parseInt(document.getElementById('comp-category').value);
    if (!name || isNaN(price) || isNaN(categoryId)) return alert('Заполни поля');
    await apiRequest('/components', 'POST', { name, manufacturer, price, stockQuantity, categoryId });
    loadAllData();
}
async function deleteComponent(id) {
    if(!confirm('Удалить?')) return;
    await apiRequest('/components/' + id, 'DELETE');
    loadAllData();
}
async function createBuild() {
    const name = document.getElementById('build-name').value;
    if (!name) return alert('Введи название');
    await apiRequest('/builds', 'POST', { name, userId: 1 });
    document.getElementById('build-name').value = '';
    loadAllData();
}
async function deleteBuild(id) {
    if(!confirm('Удалить?')) return;
    await apiRequest('/builds/' + id, 'DELETE');
    loadAllData();
}
async function addItemToBuild(buildId) {
    const select = document.getElementById('add-item-' + buildId);
    const componentId = parseInt(select.value);
    await apiRequest(`/builds/${buildId}/items`, 'POST', { componentId, quantity: 1 });
    loadAllData();
}

window.onload = initApp;