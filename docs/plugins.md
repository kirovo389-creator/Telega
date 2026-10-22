# Telega plugin format (initial implementation)

Telega's plugin manager imports local `.plugin` files, parses static metadata and stores them in the app-private `files/telega_plugins` directory. It does not execute Python source or download remote code.

A source file may declare the metadata used by exteraGram-style plugins:

```python
__id__ = "sample_plugin"
__name__ = "Sample Plugin"
__description__ = "Example metadata"
__author__ = "Your name"
__version__ = "1.0.0"
```

The manager validates `__id__` and `__name__`, enforces a 1 MiB import limit, and displays the available metadata. This is an import/management foundation, not yet a compatible Python runtime: BasePlugin lifecycle, request/message hooks, settings widgets, and Xposed-style Java hooks are not enabled. Do not treat a plugin file as trusted merely because its metadata is valid.
