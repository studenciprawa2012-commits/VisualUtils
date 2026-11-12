package dev.visualutils;

/** Lista modulow widocznych w menu. Kazdy mozna wlaczyc i wylaczyc. */
public enum Module {
    TRACERS("Tracery (linie do graczy)"),
    ESP("ESP (ramki wokół graczy)"),
    NAMETAGS("Nametagi+ (nick, HP, dystans)"),
    MOBS("Pokazuj też moby"),
    FULLBRIGHT("Fullbright (pełna jasność)"),
    ZOOM("Zoom (FOV 30)"),
    INFO("Info (koordynaty i kierunek)");

    private final String label;

    Module(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
