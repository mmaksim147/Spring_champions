const API = 'http://localhost:8080';

async function apiRequest(endpoint, method = 'GET', body = null, authRequired = false) {
    const headers = { 'Content-Type': 'application/json' };
    if (authRequired) {
        const token = localStorage.getItem('token');
        if (token) headers['Authorization'] = 'Bearer ' + token;
    }

    const options = { method, headers };
    if (body) options.body = JSON.stringify(body);

    const res = await fetch(API + endpoint, options);

    if (!res.ok) {
        const errorData = await res.json().catch(() => ({}));
        throw new Error(errorData.message || 'Ошибка сети');
    }
    return res.json();
}