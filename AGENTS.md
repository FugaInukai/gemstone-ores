# Development notes

- Target Minecraft Java 26.2, Fabric, JDK 25. Do not change the target version without a user request.
- Preserve ruby, sapphire, emerald and amethyst as separate mod materials; no gold or silver additions.
- Keep gemstone equipment at iron performance. Generation frequency is per gem, roughly 1.2 times diamond.
- Commit completed work in meaningful increments, as requested by the repository owner. Never commit build outputs, credentials or local runtime configuration.
- Before delivery, build with Gradle. When changing gameplay or data, also run the client integration test.
- Generated assets/data live in src/main/resources. Update tools/generate_resources.py when changing generated resources.
