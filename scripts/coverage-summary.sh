#!/bin/sh
# Prints total JaCoCo coverage from the CSV report produced by `mvn test`.
CSV="${1:-target/site/jacoco/jacoco.csv}"
[ -f "$CSV" ] || { echo "No coverage report at $CSV" >&2; exit 0; }
awk -F, 'NR>1 {im+=$4; ic+=$5; lm+=$8; lc+=$9; bm+=$6; bc+=$7}
  END {
    printf "Coverage: instructions %.1f%%, branches %.1f%%, lines %.1f%%\n",
      100*ic/(im+ic), (bm+bc) ? 100*bc/(bm+bc) : 100, 100*lc/(lm+lc)
  }' "$CSV"
