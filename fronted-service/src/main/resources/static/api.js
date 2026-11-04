const API = 'http://localhost:8080';

async function apiRequest(endpoint, method = 'GET', body = null) {
    const headers = { 'Content-Type': 'application/json' };
    const token = localStorage.getItem('token');
    if (token) headers['Authorization'] = 'Bearer ' + token;

    const options = { method, headers };
    if (body) options.body = JSON.stringify(body);

    const res = await fetch(API + endpoint, options);

    if (res.status === 401 || res.status === 403) {
        localStorage.removeItem('token');
        window.location.href = '/login.html';
        throw new Error('Нет доступа');
    }
    if (!res.ok) throw new Error('Ошибка запроса');
    return res.json();
}

// Проверка, залогинен ли юзер
function checkAuth() {
    const token = localStorage.getItem('token');
    if (!token) window.location.href = '/login.html';
}