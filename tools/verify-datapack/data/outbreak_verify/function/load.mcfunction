scoreboard objectives add ob_verify dummy
# forceload takes CHUNK coordinates: chunks (0,0) and (1,1) = blocks x/z 0..31
forceload add 0 0 1 1
scoreboard players set #run ob_verify 1
scoreboard players set #ticks ob_verify 0
