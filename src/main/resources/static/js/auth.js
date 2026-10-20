// static/js/auth.js
// Xử lý riêng cho các trang đăng nhập / đăng ký / đặt lại mật khẩu.

(function () {
  var container = document.getElementById("authContainer");
  if (!container) return;

  // 1. Chuyển qua lại giữa phần ĐĂNG NHẬP và phần GIỚI THIỆU
  function setIntro(show) {
    container.classList.toggle("show-intro", show);
    container.querySelectorAll(".panel-login, .overlay-hello").forEach(function (el) {
      el.setAttribute("aria-hidden", show ? "true" : "false");
    });
    container.querySelectorAll(".panel-intro, .overlay-back").forEach(function (el) {
      el.setAttribute("aria-hidden", show ? "false" : "true");
    });
    // Không cho Tab vào các ô nhập đang bị ẩn
    container.querySelectorAll(".panel-login input, .panel-login button, .panel-login a").forEach(function (el) {
      el.tabIndex = show ? -1 : 0;
    });
    history.replaceState(null, "", show ? "#gioi-thieu" : location.pathname + location.search);
  }

  container.querySelectorAll("[data-toggle-panel]").forEach(function (btn) {
    btn.addEventListener("click", function () {
      setIntro(!container.classList.contains("show-intro"));
    });
  });

  // Mở thẳng phần giới thiệu nếu URL có #gioi-thieu
  if (container.classList.contains("has-intro") && location.hash === "#gioi-thieu") {
    setIntro(true);
  }

  // 2. Kiểm tra xác nhận mật khẩu ở phía trình duyệt (Đăng ký & Đặt lại mật khẩu)
  var pwd = document.getElementById("password");
  var confirm = document.getElementById("confirmPassword");
  if (pwd && confirm) {
    var form = confirm.closest("form");
    var hint = confirm.closest(".form-group").querySelector(".field-error");

    function check() {
      var ok = !confirm.value || pwd.value === confirm.value;
      if (hint) {
        hint.textContent = ok ? "" : "Mật khẩu xác nhận không khớp!";
      }
      return ok;
    }
    confirm.addEventListener("input", check);
    pwd.addEventListener("input", check);
    form.addEventListener("submit", function (e) {
      if (pwd.value !== confirm.value) {
        e.preventDefault();
        if (hint) hint.textContent = "Mật khẩu xác nhận không khớp!";
        confirm.focus();
      }
    });
  }
})();