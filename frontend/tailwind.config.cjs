module.exports = {
    content: ["./index.html", "./src/**/*.{js,jsx,ts,tsx}"],
    theme: {
        extend: {
            colors: {
                primary: "#f20db9",
                "background-light": "#f8f5f8",
                "background-dark": "#22101e",
            },
            fontFamily: {
                display: ["Space Grotesk", "sans-serif"],
            },
            borderRadius: {
                DEFAULT: "0.5rem",
                lg: "1rem",
                xl: "1.5rem",
                full: "9999px",
            },
        },
    },
    plugins: [],
};