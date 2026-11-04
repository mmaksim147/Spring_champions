let allProducts = [];
let allCategories = [];

window.onload = async () => {
    checkAuthState();
    await loadCategoriesAndProducts();
};

// Проверка авторизации и роли
function checkAuthState() {
    const token = localStorage.getItem('token');
    const authBtns = document.getElementById('auth-buttons');
    const userProfile = document.getElementById('user-profile');

    if (token) {
        authBtns.classList.add('hidden');
        userProfile.classList.remove('hidden');

        // Пытаемся расшифровать JWT, чтобы достать роль
        try {
            const payload = JSON.parse(atob(token.split('.')[1]));
            // Роль в JWT обычно лежит в поле 'roles' или 'authorities'
            const roles = payload.roles || payload.authorities || [];

            // Если роль админа найдена - показываем кнопку
            if (roles.includes('ROLE_ADMIN') || roles.includes('ADMIN')) {
                document.getElementById('admin-btn').style.display = 'inline-block';
            }
        } catch (e) {
            console.log("Не удалось прочитать токен");
        }
    } else {
        authBtns.classList.remove('hidden');
        userProfile.classList.add('hidden');
    }
}

// --- КАТАЛОГ ---
async function loadCategoriesAndProducts() {
    try {
        allCategories = await apiRequest('/categories');
        if (allCategories.content) allCategories = allCategories.content;

        allProducts = await apiRequest('/components');
        if (allProducts.content) allProducts = allProducts.content;

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
        grid.innerHTML = '<p style="color: #64748b;">В этой категории пока нет товаров.</p>';
        return;
    }

    products.forEach(p => {
        grid.innerHTML += `
            <div class="product-card">
                <div class="brand">${p.manufacturer || 'No Name'}</div>
                <h4>${p.name}</h4>
                <div class="price">${p.price.toLocaleString('ru-RU')} ₽</div>
                <button class="buy-btn" onclick="alert('Товар добавлен в корзину!')">В корзину</button>
            </div>
        `;
    });
}

function logout() {
    localStorage.removeItem('token');
    window.location.reload();
}