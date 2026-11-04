window.onload = () => { loadAdminData(); };

function switchAdminTab(tab, btn) {
    document.querySelectorAll('.sidebar a').forEach(a => a.classList.remove('active'));
    btn.classList.add('active');
    document.getElementById('admin-products').classList.add('hidden');
    document.getElementById('admin-categories').classList.add('hidden');
    document.getElementById('admin-users').classList.add('hidden');
    document.getElementById('admin-' + tab).classList.remove('hidden');
}

async function loadAdminData() {
    try {
        // Товары
        const products = await apiRequest('/components');
        const pList = products.content || products;
        const pTbody = document.querySelector('#admin-products-table tbody');
        pTbody.innerHTML = pList.map(p => `<tr><td>${p.id}</td><td>${p.name}</td><td>${p.price} ₽</td><td><button class="btn-outline" style="padding: 5px 10px;" onclick="deleteItem('/components/${p.id}')">Удалить</button></td></tr>`).join('');

        // Категории
        const cats = await apiRequest('/categories');
        const cList = cats.content || cats;
        const cTbody = document.querySelector('#admin-categories-table tbody');
        cTbody.innerHTML = cList.map(c => `<tr><td>${c.id}</td><td>${c.name}</td><td><button class="btn-outline" style="padding: 5px 10px;" onclick="deleteItem('/categories/${c.id}')">Удалить</button></td></tr>`).join('');

        // Пользователи (предполагаем, что такой эндпоинт есть в auth-service)
        try {
            const users = await apiRequest('/admin/users');
            const uList = users.content || users;
            document.querySelector('#admin-users-table tbody').innerHTML = uList.map(u => `<tr><td>${u.id}</td><td>${u.username}</td><td>${u.email}</td></tr>`).join('');
        } catch (e) { console.log('Нет доступа к юзерам или ручки нет'); }

    } catch (err) { console.error(err); }
}

async function deleteItem(url) {
    if(!confirm('Удалить?')) return;
    await apiRequest(url, 'DELETE');
    location.reload();
}

function logout() {
    localStorage.removeItem('token');
    window.location.href = '/';
}