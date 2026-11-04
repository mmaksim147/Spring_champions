async function login() {
    const u = document.getElementById('login-username').value;
    const p = document.getElementById('login-password').value;
    const err = document.getElementById('login-error');
    err.innerText = 'Загрузка...';

    try {
        const res = await fetch(API + '/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: u, password: p })
        });

        if (!res.ok) throw new Error('Неверный логин или пароль');
        const data = await res.json();
        localStorage.setItem('token', data.token);
        location.reload();
    } catch (e) {
        err.innerText = e.message;
    }
}

function logout() {
    localStorage.removeItem('token');
    location.reload();
}