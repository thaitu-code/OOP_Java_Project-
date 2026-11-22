(() => {
    'use strict';
    const root = document.getElementById('badge-admin');
    if (!root) return;
    const form = document.getElementById('badge-form');
    const previewIcon = document.getElementById('badge-preview-icon');
    // Never interpret user input as HTML or executable image URLs.
    function renderIcon(container, raw) {
        const value = (raw || '').trim();
        container.replaceChildren();
        if (/^https?:\/\//i.test(value)) {
            try {
                const url = new URL(value);
                if (!['http:', 'https:'].includes(url.protocol)) throw new Error('Unsupported image URL');
                const image = document.createElement('img');
                image.alt = '';
                image.referrerPolicy = 'no-referrer';
                image.addEventListener('error', () => {
                    if (image.parentNode === container) container.textContent = '🏅';
                }, {once: true});
                image.src = url.href;
                container.append(image);
                return;
            } catch (_) { /* Invalid image URLs use the default medal. */ }
            container.textContent = '🏅';
            return;
        }
        container.textContent = value && !/[:<>/]/.test(value) && [...value].length <= 16 ? value : '🏅';
    }
    let previousIcon;
    function updatePreview() {
        document.getElementById('badge-preview-name').textContent = form.elements.name.value.trim() || 'Tên huy hiệu';
        document.getElementById('badge-preview-description').textContent = form.elements.description.value.trim() || 'Mô tả huy hiệu sẽ hiển thị tại đây.';
        const threshold = form.elements.thresholdValue;
        document.getElementById('badge-preview-threshold').textContent = threshold.value && threshold.validity.valid
            ? `${Number(threshold.value).toLocaleString('vi-VN')} ngày liên tiếp` : 'Chưa đặt số ngày';
        const icon = form.elements.icon.value;
        if (icon !== previousIcon) { renderIcon(previewIcon, icon); previousIcon = icon; }
    }
    form.addEventListener('input', updatePreview);
    updatePreview();
    const rows = [...root.querySelectorAll('[data-badge-row]')];
    for (const row of rows) {
        const icon = row.querySelector('.badge-list-icon');
        renderIcon(icon, icon.textContent);
    }
    const normalize = text => text.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase();
    const search = document.getElementById('badge-search');
    const filters = [...root.querySelectorAll('[data-badge-filter]')];
    const index = rows.map(row => ({row, active: row.dataset.active === 'true', text: normalize(row.querySelector('.badge-identity>div').textContent)}));
    let selected = 'all';
    function applyFilter() {
        const query = normalize(search.value.trim());
        let visible = 0;
        for (const item of index) {
            const matchesStatus = selected === 'all' || (selected === 'active' ? item.active : !item.active);
            item.row.hidden = !matchesStatus || !item.text.includes(query);
            if (!item.row.hidden) visible++;
        }
        const empty = document.getElementById('badge-empty');
        empty.hidden = visible > 0;
        empty.textContent = rows.length ? 'Không tìm thấy huy hiệu phù hợp. Thử từ khóa hoặc trạng thái khác.' : 'Chưa có huy hiệu nào. Tạo huy hiệu đầu tiên ở phía trên.';
        document.getElementById('badge-results').textContent = `Hiển thị ${visible} / ${rows.length} huy hiệu.`;
        filters.forEach(button => button.setAttribute('aria-pressed', String(button.dataset.badgeFilter === selected)));
    }
    filters.forEach(button => button.addEventListener('click', () => { selected = button.dataset.badgeFilter; applyFilter(); }));
    search.addEventListener('input', applyFilter);
    document.getElementById('badge-list-controls').hidden = false;
    applyFilter();
})();
