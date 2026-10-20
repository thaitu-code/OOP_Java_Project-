// static/js/main.js
// Các hàm dùng chung cho mọi trang (được nhúng trực tiếp bằng thẻ <script>).

// Nút con mắt: hiện / ẩn mật khẩu
document.querySelectorAll("[data-toggle-password]").forEach(function (btn) {
  btn.addEventListener("click", function () {
    var input = btn.closest(".field").querySelector("input");
    var show = input.type === "password";
    input.type = show ? "text" : "password";
    btn.classList.toggle("is-visible", show);
    btn.setAttribute("aria-label", show ? "Ẩn mật khẩu" : "Hiện mật khẩu");
  });
});