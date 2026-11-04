// Глобальные данные
let allProducts = [];
let allCategories = [];

// Инициализация при загрузке
window.onload = async () => {
    checkAuthState();
    await loadCategoriesAndProducts();
};

// --- АВТОРИЗАЦИЯ ---

function checkAuthState() {
    const token = localStorage.getItem('token');
    const authBtns = document.getElementById('auth-buttons');
    const userProfile = document.getElementById('user-profile');

    if (token) {
        authBtns.classList.add('hidden');
        userProfile.classList.remove('hidden');
    } else {
        authBtns.classList.remove('hidden');
        userProfile.classList.add('hidden');
    }
}

function openAuth(type) {
    document.getElementById('auth-modal').classList.remove('hidden');
    document.getElementById('auth-error').innerText = '';

    if (type === 'login') {
        document.getElementById('modal-title').innerText = 'Вход в аккаунт';
        document.getElementById('login-form').classList.remove('hidden');
        document.getElementById('register-form').classList.add('hidden');
    } else {
        document.getElementById('modal-title').innerText = 'Создание аккаунта';
        document.getElementById('login-form').classList.add('hidden');
        document.getElementById('register-form').classList.remove('hidden');
    }
}

function closeAuth() {
    document.getElementById('auth-modal').classList.add('hidden');
}

function logout() {
    localStorage.removeItem('token');
    checkAuthState();
    location.reload(); // Перезагружаем, чтобы сбросить состояние
}

// Обработка форм
document.getElementById('login-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const username = document.getElementById('login-username').value;
    const password = document.getElementById('login-password').value;

    try {
        const data = await apiRequest('/auth/login', 'POST', { username, password });
        localStorage.setItem('token', data.token);
        closeAuth();
        checkAuthState();
        // Можно перезагрузить страницу, чтобы применить права
        location.reload();
    } catch (err) {
        document.getElementById('auth-error').innerText = err.message;
    }
});

document.getElementById('register-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const username = document.getElementById('reg-username').value;
    const email = document.getElementById('reg-email').value;
    const password = document.getElementById('reg-password').value;

    try {
        await apiRequest('/auth/register', 'POST', { username, email, password });
        alert('Регистрация успешна! Теперь войдите.');
        openAuth('login');
    } catch (err) {
        document.getElementById('auth-error').innerText = err.message;
    }
});

// --- КАТАЛОГ ---

async function loadCategoriesAndProducts() {
    try {
        // Загружаем категории
        allCategories = await apiRequest('/categories');
        if (allCategories.content) allCategories = allCategories.content; // Если пагинация

        // Загружаем товары
        allProducts = await apiRequest('/components');
        if (allProducts.content) allProducts = allProducts.content; // Если пагинация

        renderCategories();
        renderProducts(allProducts);
    } catch (err) {
        console.error('Ошибка загрузки:', err);
        document.getElementById('products-grid').innerHTML = '<p>Не удалось загрузить каталог.</p>';
    }
}

function renderCategories() {
    const container = document.getElementById('category-filters');
    allCategories.forEach(cat => {
        container.innerHTML += `
            <button class="filter-btn" onclick="filterCatalog(${cat.id}, this)">${cat.name}</button>
        `;
    });
}

function filterCatalog(categoryId, btn) {
    document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');

    if (categoryId === null) {
        renderProducts(allProducts);
    } else {
        const filtered = allProducts.filter(p => p.categoryId === categoryId || p.category?.id === categoryId);
        renderProducts(filtered);
    }
}

function renderProducts(products) {
    const grid = document.getElementById('products-grid');
    grid.innerHTML = '';

    if (products.length === 0) {
        grid.innerHTML = '<p style="color: var(--text-muted)">В этой категории пока нет товаров.</p>';
        return;
    }

    products.forEach(p => {
        grid.innerHTML += `
            <div class="product-card">
                <div class="brand">${p.manufacturer || 'No Name'}</div>
                <h4>${p.name}</h4>
                <div class="price">${p.price.toLocaleString('ru-RU')} ₽</div>
                <button class="buy-btn" onclick="addToCart(${p.id})">В корзину</button>
            </div>
        `;
    });
}

// --- КОРЗИНА (Заглушка для будущего) ---
function addToCart(productId) {
    alert('Товар добавлен в корзину! (Логику корзины допишет твой друг)');
}