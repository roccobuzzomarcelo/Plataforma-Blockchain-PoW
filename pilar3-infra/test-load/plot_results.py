#!/usr/bin/env python3
"""Graficos y resumen de las pruebas de la seccion 3.3.

Uso:
    python plot_results.py results/20260929-101500-vm_off
    python plot_results.py results/*-vm_off results/*-vm_on --out charts

Acepta carpetas (con results.csv adentro) o archivos .csv. Si se pasan varias
corridas con distinto -Label se superponen en cada grafico (por ejemplo
vm_off vs vm_on para comparar 2 y 3 workers).

Requiere: pip install pandas matplotlib
"""
import argparse
import sys
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
import pandas as pd  # noqa: E402

NUM_COLS = ["rep", "workers", "tx_count", "prefix_len_req", "prefix_len_eff", "chunks",
            "range", "fragment_pct", "ingest_ms", "ingest_server_ms", "flush_ms",
            "total_ms", "block_index", "nonce", "tx_in_block"]


def load(paths):
    frames = []
    for p in paths:
        p = Path(p)
        csv = p / "results.csv" if p.is_dir() else p
        if not csv.exists():
            sys.exit(f"No encuentro {csv}")
        frames.append(pd.read_csv(csv, encoding="utf-8-sig"))
    df = pd.concat(frames, ignore_index=True)
    for c in NUM_COLS:
        if c in df.columns:
            df[c] = pd.to_numeric(df[c], errors="coerce")
    # prefijo efectivo si se pudo leer del bloque; si no, el pedido
    df["prefix_len"] = df["prefix_len_eff"].fillna(df["prefix_len_req"])
    return df


def ok_only(d):
    return d[(d["status"] == "OK") & d["total_ms"].notna()]


def stats(g):
    return pd.Series({"n": len(g), "mean": g.mean(), "std": g.std(ddof=1) if len(g) > 1 else 0.0})


def plot_prefix(df, out):
    d = df[df["experiment"] == "prefix"]
    if d.empty:
        return None
    fig, ax = plt.subplots(figsize=(8, 5))
    for label, gl in ok_only(d).groupby("label"):
        agg = gl.groupby("prefix_len")["total_ms"].apply(stats).unstack()
        ax.scatter(gl["prefix_len"], gl["total_ms"], alpha=0.35, s=18)
        ax.errorbar(agg.index, agg["mean"], yerr=agg["std"], marker="o", capsize=3,
                    label=f"{label} (workers={int(gl['workers'].median())})")
    # referencia teorica: x16 por caracter, anclada en la primera media > 300 ms
    ref = ok_only(d).groupby("prefix_len")["total_ms"].mean()
    big = ref[ref > 300]
    if not big.empty:
        n0, t0 = big.index[0], big.iloc[0]
        xs = [x for x in sorted(set(d["prefix_len"].dropna().astype(int))) if x >= n0]
        ax.plot(xs, [t0 * 16 ** (x - n0) for x in xs], "k--", alpha=0.4, label="referencia x16 por caracter")
    ax.set_yscale("log")
    ax.set_xlabel("Longitud del prefijo (caracteres hexadecimales)")
    ax.set_ylabel("Tiempo hasta confirmar el bloque (ms, escala log)")
    ax.set_title("Dificultad: tiempo de minado vs longitud del prefijo")
    fails = d[d["status"] != "OK"].groupby("prefix_len").size()
    for x, n in fails.items():
        ax.annotate(f"{n} sin resolver", xy=(x, 0.97), xycoords=("data", "axes fraction"),
                    ha="center", va="top", color="red", fontsize=8,
                    bbox=dict(boxstyle="round", fc="white", ec="red", alpha=0.8))
    ax.grid(True, which="both", alpha=0.3)
    ax.legend()
    fig.tight_layout()
    f = out / "prefix_vs_time.png"
    fig.savefig(f, dpi=150)
    plt.close(fig)
    return f


def plot_fragmentation(df, out):
    d = ok_only(df[df["experiment"] == "fragmentation"])
    if d.empty:
        return None
    labels = sorted(d["label"].unique())
    pcts = sorted(d["fragment_pct"].dropna().unique())
    fig, ax = plt.subplots(figsize=(8, 5))
    width = 0.8 / max(1, len(labels))
    for i, label in enumerate(labels):
        gl = d[d["label"] == label]
        means, stds = [], []
        for p in pcts:
            v = gl[gl["fragment_pct"] == p]["total_ms"]
            means.append(v.mean() if len(v) else 0)
            stds.append(v.std(ddof=1) if len(v) > 1 else 0)
        xs = [k + i * width for k in range(len(pcts))]
        ax.bar(xs, means, width, yerr=stds, capsize=3, label=label)
    ticks = [k + width * (len(labels) - 1) / 2 for k in range(len(pcts))]
    chunks = d.groupby("fragment_pct")["chunks"].first()
    ax.set_xticks(ticks)
    ax.set_xticklabels([f"{p:g}%\n({int(chunks[p])} chunks)" for p in pcts])
    ax.set_xlabel("Tamano de fragmento (% del rango total)")
    ax.set_ylabel("Tiempo hasta confirmar el bloque (ms)")
    ax.set_title("Fragmentacion del trabajo: eficiencia de la distribucion")
    ax.grid(True, axis="y", alpha=0.3)
    ax.legend()
    fig.tight_layout()
    f = out / "fragmentation.png"
    fig.savefig(f, dpi=150)
    plt.close(fig)
    return f


def plot_bulk(df, out):
    d = df[df["experiment"] == "bulk"]
    if d.empty:
        return None
    fig, axes = plt.subplots(1, 2, figsize=(11, 4.5))
    for label, gl in ok_only(d).groupby("label"):
        for ax, col in zip(axes, ["total_ms", "ingest_ms"]):
            agg = gl.groupby("tx_count")[col].agg(["mean", "std"]).fillna(0)
            ax.errorbar(agg.index, agg["mean"], yerr=agg["std"], marker="o", capsize=3, label=label)
    titles = ["Minado de un bloque (flush hasta confirmacion)", "Ingesta de las transacciones en el pool"]
    for ax, t in zip(axes, titles):
        ax.set_xscale("log")
        ax.set_yscale("log")
        ax.set_xlabel("Transacciones por bloque")
        ax.set_ylabel("Tiempo (ms)")
        ax.set_title(t, fontsize=10)
        ax.grid(True, which="both", alpha=0.3)
        ax.legend()
    fig.tight_layout()
    f = out / "bulk_transactions.png"
    fig.savefig(f, dpi=150)
    plt.close(fig)
    return f


def plot_gpu(df, out):
    d = df[df["experiment"] == "gpu"]
    d = d[d["total_ms"].notna()]
    if d.empty:
        return None
    fig, ax = plt.subplots(figsize=(9, 4.8))
    palette = {}
    for label, gl in d.groupby("label"):
        gl = gl.sort_values("rep")
        ax.plot(gl["rep"], gl["total_ms"], color="grey", alpha=0.4, zorder=1)
        for phase, gp in gl.groupby("phase", sort=False):
            color = palette.setdefault(phase, f"C{len(palette)}")
            ax.scatter(gp["rep"], gp["total_ms"], color=color, s=60, zorder=2,
                       label=f"{label}: {phase}")
            for _, r in gp.iterrows():
                if pd.notna(r["prefix_len"]):
                    ax.annotate(f"prefijo {int(r['prefix_len'])}", (r["rep"], r["total_ms"]),
                                textcoords="offset points", xytext=(0, 7), ha="center", fontsize=7)
    ax.set_yscale("log")
    ax.set_xlabel("Bloque (orden de la prueba)")
    ax.set_ylabel("Tiempo hasta confirmar (ms, escala log)")
    ax.set_title("Ingreso y egreso de un minero GPU simulado (ajuste automatico del prefijo)")
    ax.grid(True, which="both", alpha=0.3)
    ax.legend(fontsize=8)
    fig.tight_layout()
    f = out / "gpu_phases.png"
    fig.savefig(f, dpi=150)
    plt.close(fig)
    return f


def summary_md(df, out):
    lines = ["# Resumen de resultados (seccion 3.3)\n",
             "Tiempo = desde el pedido de flush hasta que la API informa el bloque confirmado "
             "(resolucion ~100 ms por el sondeo). Solo se promedian las corridas OK.\n"]
    for exp, title in [("prefix", "Dificultad (prefijo)"), ("fragmentation", "Fragmentacion"),
                       ("bulk", "Transacciones por bloque"), ("gpu", "GPU simulada")]:
        d = df[df["experiment"] == exp]
        if d.empty:
            continue
        lines.append(f"\n## {title}\n")
        lines.append("| label | config | workers | chunks | ok | fallidas | media (ms) | mediana (ms) | desvio (ms) | min (ms) | max (ms) |")
        lines.append("|---|---|---|---|---|---|---|---|---|---|---|")
        for (label, config), g in d.groupby(["label", "config"], sort=False):
            ok = g[(g["status"] == "OK") & g["total_ms"].notna()]["total_ms"]
            fmt = lambda x: "-" if pd.isna(x) else f"{x:,.0f}".replace(",", ".")
            lines.append(
                f"| {label} | {config} | {int(g['workers'].median())} | {int(g['chunks'].median())} | {len(ok)} | "
                f"{len(g) - len(ok)} | {fmt(ok.mean())} | {fmt(ok.median())} | "
                f"{fmt(ok.std(ddof=1) if len(ok) > 1 else float('nan'))} | {fmt(ok.min())} | {fmt(ok.max())} |")
    f = out / "summary.md"
    f.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return f


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("inputs", nargs="+", help="carpetas de resultados o archivos .csv")
    ap.add_argument("--out", help="carpeta de salida (por defecto: charts/ junto al primer resultado)")
    args = ap.parse_args()

    df = load(args.inputs)
    first = Path(args.inputs[0])
    out = Path(args.out) if args.out else (first if first.is_dir() else first.parent) / "charts"
    out.mkdir(parents=True, exist_ok=True)

    made = [f for f in (plot_prefix(df, out), plot_fragmentation(df, out), plot_bulk(df, out),
                        plot_gpu(df, out), summary_md(df, out)) if f]
    print(f"{len(df)} corridas leidas; archivos generados en {out}:")
    for f in made:
        print("  -", f.name)


if __name__ == "__main__":
    main()
