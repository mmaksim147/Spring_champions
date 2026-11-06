document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('[data-image-carousel]').forEach(carousel => {
        const images = Array.from(
            carousel.querySelectorAll('.carousel-slide')
        );

        if (images.length <= 1) {
            if (previous) previous.hidden = true;
            if (next) next.hidden = true;
            if (indicators) indicators.hidden = true;
            return;
        }

        const previous = carousel.querySelector('.carousel-prev');
        const next = carousel.querySelector('.carousel-next');
        const indicators = carousel.querySelector('.carousel-indicators');

        if (images.length === 1) return;

        let current = 0;
        let timer = null;
        let paused = false;
        let touchStartX = 0;

        const dots = images.map((image, index) => {
            const dot = document.createElement('button');

            dot.type = 'button';
            dot.className = 'carousel-dot';
            dot.setAttribute('aria-label', `Фотография ${index + 1}`);

            dot.addEventListener('click', () => {
                show(index);
                restartTimer();
            });

            indicators.appendChild(dot);
            return dot;
        });

        function show(index) {
            current = (index + images.length) % images.length;

            images.forEach((image, i) => {
                image.classList.toggle('is-active', i === current);
            });

            dots.forEach((dot, i) => {
                dot.classList.toggle('is-active', i === current);
                dot.setAttribute(
                    'aria-current',
                    i === current ? 'true' : 'false'
                );
            });
        }

        function stopTimer() {
            if (timer !== null) {
                clearInterval(timer);
                timer = null;
            }
        }

        function startTimer() {
            stopTimer();

            if (
                paused ||
                document.hidden ||
                window.matchMedia('(prefers-reduced-motion: reduce)').matches
            ) return;

            timer = setInterval(() => {
                show(current + 1);
            }, 4000);
        }

        function restartTimer() {
            stopTimer();
            startTimer();
        }

        previous.addEventListener('click', () => {
            show(current - 1);
            restartTimer();
        });

        next.addEventListener('click', () => {
            show(current + 1);
            restartTimer();
        });

        carousel.addEventListener('mouseenter', () => {
            paused = true;
            stopTimer();
        });

        carousel.addEventListener('mouseleave', () => {
            paused = false;
            startTimer();
        });

        carousel.addEventListener('focusin', stopTimer);

        carousel.addEventListener('focusout', event => {
            if (!carousel.contains(event.relatedTarget)) {
                paused = false;
                startTimer();
            }
        });

        carousel.addEventListener('touchstart', event => {
            touchStartX = event.changedTouches[0].screenX;
        }, { passive: true });

        carousel.addEventListener('touchend', event => {
            const difference =
                event.changedTouches[0].screenX - touchStartX;

            if (Math.abs(difference) > 45) {
                show(current + (difference < 0 ? 1 : -1));
                restartTimer();
            }
        }, { passive: true });

        document.addEventListener('visibilitychange', () => {
            if (document.hidden) {
                stopTimer();
            } else {
                startTimer();
            }
        });

        show(0);
        startTimer();
    });
});
