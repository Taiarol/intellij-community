FROM felipetaiarol/test

RUN groupadd --gid 33333 gitpod
RUN adduser --no-log-init --create-home --home-dir /home/gitpod --shell /bin/bash --uid 33333 --gid 33333 gitpod

USER gitpod
