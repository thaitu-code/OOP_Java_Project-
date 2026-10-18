// src/main/resources/js/auth.js

function switchTab(tab) {
  const loginTabBtn = document.getElementById("loginTabBtn");
  const signupTabBtn = document.getElementById("signupTabBtn");
  const loginForm = document.getElementById("loginForm");
  const signupForm = document.getElementById("signupForm");

  if (tab === "login") {
    loginTabBtn.classList.add("active");
    signupTabBtn.classList.remove("active");
    loginForm.classList.add("active");
    signupForm.classList.remove("active");
  } else {
    signupTabBtn.classList.add("active");
    loginTabBtn.classList.remove("active");
    signupForm.classList.add("active");
    loginForm.classList.remove("active");
  }
}
