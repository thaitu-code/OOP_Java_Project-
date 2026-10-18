// src/main/resources/js/main.js

// Tự động chèn file auth.js vào trang mà HTML không cần gọi trực tiếp
const script = document.createElement("script");
script.src = "/src/main/resources/js/auth.js";
document.head.appendChild(script);

console.log("Main.js đã tải và đang gọi auth.js!");
