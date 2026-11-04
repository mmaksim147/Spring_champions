// Теперь API Gateway на 8080, но так как мы в 8085, нужно стучаться на 8080
const API = 'http://localhost:8080';

function getHeaders() {
    return {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + localStorage.getItem('token')
    };
}

async function apiRequest(endpoint, method = 'GET', body = null) {
    const options = { method, headers: getHeaders() };
    if (body) options.body = JSON.stringify(body);

    const res = await fetch(API + endpoint, options);
    if (res.status === 401 || res.status === 403) {
        logout();
        throw new Error('Сессия истекла');
    }
    if (!res.ok) throw new Error('Ошибка запроса: ' + res.status);
    return res.json();
}